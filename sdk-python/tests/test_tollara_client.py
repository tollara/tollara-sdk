"""Integration-style tests for unified `TollaraClient` (Java parity)."""

import pytest
import responses

from tollara_service_sdk.client import DEFAULT_API_URL, TollaraClient, ENV_API_URL

SERVICE_KEY = "k"
SERVICE_ID = "550e8400-e29b-41d4-a716-446655440000"
SERVICE_SECRET = "test-agent-secret"


@responses.activate
def test_requests_use_default_api_base_when_only_secret():
    base = DEFAULT_API_URL.rstrip("/")
    responses.add(
        responses.GET,
        f"{base}/api/requests/x/status",
        body="{}",
        status=200,
    )
    TollaraClient(service_secret=SERVICE_SECRET, service_id=SERVICE_ID).get_request_status("x", SERVICE_KEY)
    assert responses.calls[0].request.url.startswith(f"{base}/api/")


def test_build_requires_service_secret():
    with pytest.raises(ValueError, match="secret"):
        TollaraClient(api_url="http://localhost:1")


@responses.activate
def test_get_request_status_uses_default_gateway_prefix():
    base = "http://localhost:59999"
    responses.add(
        responses.GET,
        f"{base}/api/requests/job-1/status",
        json={"state": "OK"},
        status=200,
    )

    client = TollaraClient(api_url=base, service_id=SERVICE_ID, service_secret=SERVICE_SECRET)
    res = client.get_request_status("job-1", SERVICE_KEY)

    assert res.ok
    assert res.status_code == 200
    assert "OK" in res.body
    assert responses.calls[0].request.headers.get("Authorization") == f"Bearer {SERVICE_KEY}"


@responses.activate
def test_report_usage_uses_default_usage_prefix():
    base = "http://localhost:59998"
    responses.add(
        responses.POST,
        f"{base}/api/usage/report",
        json={
            "status": "ok",
            "isOverLimit": False,
            "remainingRequestsPerPeriod": 1,
        },
        status=200,
    )

    client = TollaraClient(api_url=base, service_id=SERVICE_ID, service_secret=SERVICE_SECRET)
    out = client.report_usage("user-1", SERVICE_ID, 1.0)

    assert out.status == "ok"
    assert responses.calls[0].request.url == f"{base}/api/usage/report"


@responses.activate
def test_custom_usage_path_prefix_on_client():
    base = "http://localhost:59997"
    responses.add(
        responses.POST,
        f"{base}/usage/api/v1/report",
        json={
            "status": "ok",
            "isOverLimit": False,
            "remainingRequestsPerPeriod": 1,
        },
        status=200,
    )

    client = TollaraClient(
        api_url=base,
        service_id=SERVICE_ID,
        service_secret=SERVICE_SECRET,
        usage_path_prefix="/usage/api/v1",
    )
    out = client.report_usage("user-1", SERVICE_ID, 1.0)
    assert out.status == "ok"
    assert responses.calls[0].request.url == f"{base}/usage/api/v1/report"


@responses.activate
def test_from_env_uses_default_base_without_agents_api_url(monkeypatch):
    monkeypatch.delenv(ENV_API_URL, raising=False)
    monkeypatch.setenv("TOLLARA_SERVICE_SECRET", SERVICE_SECRET)
    monkeypatch.setenv("TOLLARA_SERVICE_ID", SERVICE_ID)
    base = DEFAULT_API_URL.rstrip("/")
    responses.add(
        responses.GET,
        f"{base}/api/requests/r1/status",
        body="{}",
        status=200,
    )

    res = TollaraClient.from_env().get_request_status("r1", SERVICE_KEY)
    assert res.ok
    assert res.status_code == 200


@responses.activate
def test_from_env_overrides_base_with_agents_api_url(monkeypatch):
    base = "http://env-from.test"
    monkeypatch.setenv(ENV_API_URL, base)
    monkeypatch.setenv("TOLLARA_SERVICE_SECRET", SERVICE_SECRET)
    monkeypatch.setenv("TOLLARA_SERVICE_ID", SERVICE_ID)
    responses.add(
        responses.GET,
        f"{base}/api/requests/r2/status",
        body="{}",
        status=200,
    )

    res = TollaraClient.from_env().get_request_status("r2", SERVICE_KEY)
    assert res.ok
