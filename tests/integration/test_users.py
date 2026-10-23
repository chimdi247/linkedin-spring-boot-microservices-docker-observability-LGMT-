import pytest

from conftest import PNG, connect, wait_until

pytestmark = pytest.mark.integration


def test_get_profile_and_404(alice):
    r = alice.get(f"/users/{alice.user_id}")
    assert r.status_code == 200
    b = r.json()
    assert b["id"] == alice.user_id and b["email"] == alice.email and b["role"] == "NORMAL_USER"
    assert "password" not in b
    assert alice.get("/users/does-not-exist").status_code == 404


def test_update_own_profile_and_skills_are_trimmed_and_deduped(alice):
    r = alice.put(f"/users/{alice.user_id}/profile", json={
        "headline": "Staff engineer", "about": "x" * 1500, "location": "Paris", "skills": [" Java ", "Kafka", "Java", ""]})
    assert r.status_code == 200
    b = r.json()
    assert b["headline"] == "Staff engineer" and b["location"] == "Paris"
    assert len(b["about"]) == 1500                       # `about` is TEXT, not varchar(255)
    assert sorted(b["skills"]) == ["Java", "Kafka"]
    assert alice.get(f"/users/{alice.user_id}").json()["skills"] is not None


def test_cannot_update_someone_elses_profile_403(alice, bob):
    assert alice.put(f"/users/{bob.user_id}/profile", json={"headline": "hacked"}).status_code == 403


def test_profile_photo_upload_only_images_and_only_self(alice, bob):
    ok = alice.post(f"/users/{alice.user_id}/profile-photo", files={"file": ("a.png", PNG, "image/png")})
    assert ok.status_code == 200
    url = ok.json()["profilePhotoUrl"]
    assert url and "avatar" in url
    assert alice.post(f"/users/{alice.user_id}/profile-photo", files={"file": ("a.txt", b"hello", "text/plain")}).status_code == 400
    assert alice.post(f"/users/{bob.user_id}/profile-photo", files={"file": ("a.png", PNG, "image/png")}).status_code == 403


def test_suggestions_exclude_self_connected_and_pending(make_user):
    me, friend, pending, stranger = (make_user(first=n) for n in ("Me", "Friend", "Pending", "Stranger"))
    connect(me, friend)
    assert me.post(f"/users/{pending.user_id}/connect").status_code == 200
    ids = {u["id"] for u in me.get("/users/suggestions?limit=50").json()}
    assert me.user_id not in ids and friend.user_id not in ids and pending.user_id not in ids
    assert stranger.user_id in ids or len(ids) == 50     # present unless the 50-row page is full of other users


def test_connection_status_transitions(alice, bob):
    assert alice.get(f"/users/{alice.user_id}/connection-status").json()["status"] == "SELF"
    assert alice.get(f"/users/{bob.user_id}/connection-status").json()["status"] == "NONE"
    assert alice.post(f"/users/{bob.user_id}/connect").status_code == 200
    s = alice.get(f"/users/{bob.user_id}/connection-status").json()
    assert s["status"] == "PENDING_SENT" and s["connectionId"]
    assert bob.get(f"/users/{alice.user_id}/connection-status").json()["status"] == "PENDING_RECEIVED"
    assert bob.put(f"/users/connection/{s['connectionId']}/accept").status_code == 200
    assert alice.get(f"/users/{bob.user_id}/connection-status").json()["status"] == "CONNECTED"
    assert bob.get(f"/users/{alice.user_id}/connection-status").json()["status"] == "CONNECTED"


def test_connect_rules_self_400_duplicate_409_unknown_404(alice, bob):
    assert alice.post(f"/users/{alice.user_id}/connect").status_code == 400
    assert alice.post("/users/no-such-user/connect").status_code == 404
    assert alice.post(f"/users/{bob.user_id}/connect").status_code == 200
    assert alice.post(f"/users/{bob.user_id}/connect").status_code == 409
    assert bob.post(f"/users/{alice.user_id}/connect").status_code == 409      # reverse direction too


def test_only_receiver_can_accept_or_reject(make_user):
    a, b, c = make_user(first="A"), make_user(first="B"), make_user(first="C")
    assert a.post(f"/users/{b.user_id}/connect").status_code == 200
    cid = b.get(f"/users/{b.user_id}/connections/pending").json()[0]["id"]
    assert a.put(f"/users/connection/{cid}/accept").status_code == 403       # requester
    assert c.put(f"/users/connection/{cid}/reject").status_code == 403       # third party
    assert b.put("/users/connection/unknown-id/accept").status_code == 404
    assert b.put(f"/users/connection/{cid}/reject").status_code == 200
    assert b.put(f"/users/connection/{cid}/accept").status_code == 409       # no longer pending
    assert a.get(f"/users/{b.user_id}/connection-status").json()["status"] == "REJECTED"


def test_pending_list_is_private(alice, bob):
    alice.post(f"/users/{bob.user_id}/connect")
    assert alice.get(f"/users/{bob.user_id}/connections/pending").status_code == 403
    pend = bob.get(f"/users/{bob.user_id}/connections/pending").json()
    assert len(pend) == 1 and pend[0]["requesterId"] == alice.user_id and pend[0]["status"] == "PENDING"


def test_connections_are_returned_for_both_sides(alice, bob):
    connect(alice, bob)
    assert [u["id"] for u in alice.get(f"/users/{alice.user_id}/connections").json()] == [bob.user_id]
    assert [u["id"] for u in bob.get(f"/users/{bob.user_id}/connections").json()] == [alice.user_id]
