import uuid

import pytest

from conftest import PNG, connect, wait_until

pytestmark = pytest.mark.integration


def _post(user, text="hello world", image=False):
    files = {"image": ("p.png", PNG, "image/png")} if image else None
    r = user.post("/posts", data={"content": text}, files=files)
    assert r.status_code == 201, r.text
    return r.json()


def test_create_post_multipart_with_and_without_image(alice):
    p = _post(alice, "first post")
    assert p["authorId"] == alice.user_id and p["content"] == "first post"
    assert p["likeCount"] == 0 and p["commentCount"] == 0 and p["likedByMe"] is False and p["imageUrl"] is None
    q = _post(alice, "with image", image=True)
    assert q["imageUrl"]


def test_author_comes_from_token_not_params(alice, bob):
    r = alice.post("/posts", data={"content": "spoof", "authorId": bob.user_id})
    assert r.status_code == 201 and r.json()["authorId"] == alice.user_id


def test_empty_post_400_and_non_image_400(alice):
    assert alice.post("/posts", data={"content": "   "}).status_code == 400
    r = alice.post("/posts", data={"content": "x"}, files={"image": ("a.txt", b"text", "text/plain")})
    assert r.status_code == 400


def test_get_post_and_404(alice):
    p = _post(alice)
    assert alice.get(f"/posts/{p['id']}").json()["id"] == p["id"]
    assert alice.get("/posts/nope").status_code == 404


def test_batch_ids_keep_order_and_skip_unknown(alice):
    ids = [_post(alice, f"post {i}")["id"] for i in range(3)]
    order = [ids[2], "missing-id", ids[0], ids[1]]
    r = alice.get("/posts", params={"ids": ",".join(order)})
    assert r.status_code == 200
    assert [p["id"] for p in r.json()] == [ids[2], ids[0], ids[1]]


def test_batch_limit_50(alice):
    r = alice.get("/posts", params={"ids": ",".join(str(uuid.uuid4()) for _ in range(51))})
    assert r.status_code == 400


def test_user_posts_newest_first(alice):
    a, b = _post(alice, "older")["id"], _post(alice, "newer")["id"]
    assert [p["id"] for p in alice.get(f"/posts/user/{alice.user_id}").json()][:2] == [b, a]


def test_like_toggle_updates_counts_and_likedByMe(alice, bob):
    p = _post(alice)
    assert bob.post(f"/posts/{p['id']}/like").text == "Post liked"
    got = bob.get(f"/posts/{p['id']}").json()
    assert got["likeCount"] == 1 and got["likedByMe"] is True
    assert alice.get(f"/posts/{p['id']}").json()["likedByMe"] is False
    assert bob.post(f"/posts/{p['id']}/like").text == "Post unliked"
    got = bob.get(f"/posts/{p['id']}").json()
    assert got["likeCount"] == 0 and got["likedByMe"] is False


def test_comments_form_and_json_newest_first_and_counts(alice, bob):
    p = _post(alice)
    c1 = bob.post(f"/posts/{p['id']}/comments", data={"content": "first"})
    assert c1.status_code == 201 and c1.json()["authorId"] == bob.user_id
    c2 = bob.post(f"/posts/{p['id']}/comments", json={"content": "second"})
    assert c2.status_code == 201
    assert bob.post(f"/posts/{p['id']}/comments", data={"content": " "}).status_code == 400
    lst = alice.get(f"/posts/{p['id']}/comments").json()
    assert [c["content"] for c in lst] == ["second", "first"]
    assert alice.get(f"/posts/{p['id']}").json()["commentCount"] == 2


def test_delete_only_by_author(alice, bob):
    p = _post(alice)
    assert bob.delete(f"/posts/{p['id']}").status_code == 403
    assert alice.delete(f"/posts/{p['id']}").status_code == 200
    assert alice.get(f"/posts/{p['id']}").status_code == 404


def test_feed_contains_own_post_and_connections_posts_after_fanout(alice, bob):
    connect(alice, bob)
    p = _post(alice, f"fanout {uuid.uuid4().hex[:6]}")
    wait_until(lambda: p["id"] in alice.get(f"/feed/{alice.user_id}").json(), 30, message="author feed")
    wait_until(lambda: p["id"] in bob.get(f"/feed/{bob.user_id}").json(), 30, message="connection feed (Kafka fan-out)")
    feed = bob.get(f"/feed/{bob.user_id}?page=0&size=10").json()
    assert feed[0] == p["id"]                              # newest first


def test_feed_is_private_and_paginated_size_is_capped(alice, bob):
    assert alice.get(f"/feed/{bob.user_id}").status_code == 403
    assert alice.delete(f"/feed/{bob.user_id}/cache").status_code == 403
    r = alice.get(f"/feed/{alice.user_id}?page=0&size=100000")
    assert r.status_code == 200 and len(r.json()) <= 50


def test_non_connected_users_do_not_receive_post(alice, bob):
    p = _post(alice, "private-ish")
    wait_until(lambda: p["id"] in alice.get(f"/feed/{alice.user_id}").json(), 30)
    assert p["id"] not in bob.get(f"/feed/{bob.user_id}").json()
