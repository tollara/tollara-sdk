"""Integration-style tests for unified `AgentVendClient` (Java parity)."""

import pytest
import responses

from agentvend_sdk.client import DEFAULT_API_URL, AgentVendClient, ENV_API_URL

AGENT_KEY = "k"
AGENT_ID = "550e8400-e29b-41d4-a716-446655440000"
AGENT_SECRET = "test-agent-secret"


@responses.activate
def test_requests_use_default_api_base_when_only_secret():
    base = DEFAULT_API_URL.rstrip("/")
    responses.add(
        responses.GET,
        f"{base}/api/requests/x/status",
        body="{}",
        status=200,
    )
    AgentVendClient(agent_secret=AGENT_SECRET, agent_id=AGENT_ID).get_request_status("x", AGENT_KEY)
    assert responses.calls[0].request.url.startswith(f"{base}/api/")


def test_build_requires_agent_secret():
    with pytest.raises(ValueError, match="secret"):
        AgentVendClient(api_url="http://localhost:1")


@responses.activate
def test_get_request_status_uses_default_gateway_prefix():
    base = "http://localhost:59999"
    responses.add(
        responses.GET,
        f"{base}/api/requests/job-1/status",
        json={"state": "OK"},
        status=200,
    )

    client = AgentVendClient(api_url=base, agent_id=AGENT_ID, agent_secret=AGENT_SECRET)
    res = client.get_request_status("job-1", AGENT_KEY)

    assert res.ok
    assert res.status_code == 200
    assert "OK" in res.body
    assert responses.calls[0].request.headers.get("Authorization") == f"Bearer {AGENT_KEY}"


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

    client = AgentVendClient(api_url=base, agent_id=AGENT_ID, agent_secret=AGENT_SECRET)
    out = client.report_usage("user-1", AGENT_ID, 1.0)

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

    client = AgentVendClient(
        api_url=base,
        agent_id=AGENT_ID,
        agent_secret=AGENT_SECRET,
        usage_path_prefix="/usage/api/v1",
    )
    out = client.report_usage("user-1", AGENT_ID, 1.0)
    assert out.status == "ok"
    assert responses.calls[0].request.url == f"{base}/usage/api/v1/report"


@responses.activate
def test_from_env_uses_default_base_without_agents_api_url(monkeypatch):
    monkeypatch.delenv(ENV_API_URL, raising=False)
    monkeypatch.setenv("AGENTVEND_AGENT_SECRET", AGENT_SECRET)
    monkeypatch.setenv("AGENTVEND_AGENT_ID", AGENT_ID)
    base = DEFAULT_API_URL.rstrip("/")
    responses.add(
        responses.GET,
        f"{base}/api/requests/r1/status",
        body="{}",
        status=200,
    )

    res = AgentVendClient.from_env().get_request_status("r1", AGENT_KEY)
    assert res.ok
    assert res.status_code == 200


@responses.activate
def test_from_env_overrides_base_with_agents_api_url(monkeypatch):
    base = "http://env-from.test"
    monkeypatch.setenv(ENV_API_URL, base)
    monkeypatch.setenv("AGENTVEND_AGENT_SECRET", AGENT_SECRET)
    monkeypatch.setenv("AGENTVEND_AGENT_ID", AGENT_ID)
    responses.add(
        responses.GET,
        f"{base}/api/requests/r2/status",
        body="{}",
        status=200,
    )

    res = AgentVendClient.from_env().get_request_status("r2", AGENT_KEY)
    assert res.ok
