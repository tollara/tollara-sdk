"""Tests for gateway polling helpers."""

import responses

from agentvend_service_sdk import get_request_result, get_request_status


@responses.activate
def test_get_request_status_sends_bearer():
    responses.add(
        responses.GET,
        "https://gw.test/api/requests/j1/status",
        json={"state": "PENDING"},
        status=200,
    )
    r = get_request_status("https://gw.test", "j1", "my-key")
    assert r.ok is True
    assert r.status_code == 200
    assert "PENDING" in r.body
    assert len(responses.calls) == 1
    assert responses.calls[0].request.headers.get("Authorization") == "Bearer my-key"


@responses.activate
def test_get_request_result_ecs_prefix():
    responses.add(
        responses.GET,
        "https://gw.test/gateway/api/v1/requests/r2/result",
        body="{}",
        status=200,
    )
    r = get_request_result("https://gw.test/", "r2", "k", gateway_path_prefix="/gateway/api/v1")
    assert r.ok is True
    assert r.status_code == 200
