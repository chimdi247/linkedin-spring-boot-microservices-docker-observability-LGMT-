import uuid

import pytest

from conftest import connect, wait_until

pytestmark = pytest.mark.integration


def test_search_people_by_name_after_indexing(make_user, alice):
    unique = f"Zyx{uuid.uuid4().hex[:8]}"
    u = make_user(first=unique, last="Searchable")
    hits = wait_until(lambda: [x for x in alice.get("/search/people", params={"q": unique}).json() if x["id"] == u.user_id],
                      40, message="user indexed via Kafka user.created")
    assert hits[0]["firstName"] == unique


def test_search_by_skill_is_case_insensitive_exact(make_user, alice):
    skill = f"skill{uuid.uuid4().hex[:8]}"
    u = make_user()
    assert u.put(f"/users/{u.user_id}/profile", json={"headline": "h", "about": "a", "location": "l", "skills": [skill]}).status_code == 200
    wait_until(lambda: any(x["id"] == u.user_id for x in alice.get("/search/skills", params={"skill": skill.upper()}).json()),
               40, message="skill indexed via user.updated")
    assert not any(x["id"] == u.user_id for x in alice.get("/search/skills", params={"skill": skill[:-2]}).json())  # exact, not prefix


def test_search_posts_fuzzy_full_text(alice):
    word = f"quokka{uuid.uuid4().hex[:6]}"
    p = alice.post("/posts", data={"content": f"learning about {word} today"}).json()
    wait_until(lambda: any(x["id"] == p["id"] for x in alice.get("/search/posts", params={"q": word}).json()),
               40, message="post indexed via post.created")


def test_blank_and_hostile_queries_do_not_break_search(alice):
    for q in ("", "   ", 'a"b', 'x\\y', '{"match_all":{}}'):
        for t in ("people", "posts"):
            assert alice.get(f"/search/{t}", params={"q": q}).status_code == 200
    assert alice.get("/search/skills", params={"skill": ""}).status_code == 200


def test_welcome_notification_and_unread_count(make_user):
    u = make_user(first="Nina")
    items = wait_until(lambda: u.get("/notifications").json(), 30, message="WELCOME notification (user.created)")
    assert items[0]["type"] == "WELCOME" and items[0]["refId"] == u.user_id and items[0]["read"] is False
    assert u.get("/notifications/unread-count").json()["count"] >= 1


def test_connection_and_like_comment_notifications(alice, bob):
    wait_until(lambda: bob.get("/notifications").json(), 30)          # welcome first
    alice.post(f"/users/{bob.user_id}/connect")
    wait_until(lambda: any(n["type"] == "CONNECTION_REQUEST" and n["refId"] == alice.user_id
                           for n in bob.get("/notifications").json()), 30, message="connection request notification")
    cid = bob.get(f"/users/{bob.user_id}/connections/pending").json()[0]["id"]
    bob.put(f"/users/connection/{cid}/accept")
    wait_until(lambda: any(n["type"] == "CONNECTION_ACCEPTED" and n["refId"] == bob.user_id
                           for n in alice.get("/notifications").json()), 30, message="accepted notification")
    p = alice.post("/posts", data={"content": "notify me"}).json()
    bob.post(f"/posts/{p['id']}/like")
    bob.post(f"/posts/{p['id']}/comments", data={"content": "hi"})
    types = lambda: {n["type"] for n in alice.get("/notifications").json() if n["refId"] == p["id"]}  # noqa: E731
    wait_until(lambda: types() == {"POST_LIKED", "POST_COMMENTED"}, 30, message="like/comment notifications")


def test_own_like_does_not_notify(alice):
    p = alice.post("/posts", data={"content": "self like"}).json()
    alice.post(f"/posts/{p['id']}/like")
    import time; time.sleep(4)
    assert not [n for n in alice.get("/notifications").json() if n["type"] == "POST_LIKED"]


def test_mark_all_read_204_and_zero_unread(make_user):
    u = make_user()
    wait_until(lambda: u.get("/notifications/unread-count").json()["count"] >= 1, 30)
    assert u.post("/notifications/read-all").status_code == 204
    assert u.get("/notifications/unread-count").json()["count"] == 0
    assert all(n["read"] for n in u.get("/notifications").json())


def test_notifications_are_per_user(alice, bob):
    wait_until(lambda: alice.get("/notifications").json(), 30)
    assert all(n["refId"] != bob.user_id for n in alice.get("/notifications").json())
