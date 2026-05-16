"""
Integration tests for the usage client against a mocked Usage API.
Uses the 'responses' library to mock HTTP; see docs/sdk-api-spec.md §3.
"""
import json
from datetime import datetime, timezone

import pytest
import responses

from agentvend_service_sdk.completion_status import CompletionStatus
from agentvend_service_sdk.hmac_utils import calculate_hmac_with_timestamp
from agentvend_service_sdk.usage_client import (
    DEFAULT_USAGE_PATH_PREFIX,
    report_completion,
    report_completion_with_result,
    report_progress,
    report_usage,
    report_usage_at,
    _usage_report_url,
    UsageReportResponse,
)

USAGE_BASE = "http://usage.test"
SERVICE_SECRET = "test-agent-secret"


def test_usage_report_url_default_and_custom():
    assert _usage_report_url(USAGE_BASE, None) == f"{USAGE_BASE}{DEFAULT_USAGE_PATH_PREFIX}/report"
    assert _usage_report_url(f"{USAGE_BASE}/", None) == f"{USAGE_BASE}{DEFAULT_USAGE_PATH_PREFIX}/report"
    assert _usage_report_url(USAGE_BASE, "/usage/api/v1") == f"{USAGE_BASE}/usage/api/v1/report"


@responses.activate
def test_report_usage_sends_signed_request_and_returns_response():
    """report_usage POSTs to /api/usage/report with signature headers and parses response."""
    responses.add(
        responses.POST,
        f"{USAGE_BASE}/api/usage/report",
        json={
            "status": "ok",
            "isOverLimit": False,
            "remainingRequestsPerPeriod": 99,
        },
        status=200,
    )

    result = report_usage_at(
        USAGE_BASE, "user-1", "service-1", 1.0, SERVICE_SECRET, timestamp=1700000000.0
    )

    assert isinstance(result, UsageReportResponse)
    assert result.status == "ok"
    assert result.is_over_limit is False
    assert result.remaining_requests_per_period == 99

    assert len(responses.calls) == 1
    req = responses.calls[0].request
    assert req.headers.get("Content-Type", "").startswith("application/json")
    assert "X-AgentVend-Signature" in req.headers
    assert "X-AgentVend-Timestamp" in req.headers
    body = json.loads(req.body)
    assert body["userId"] == "user-1"
    assert body["serviceId"] == "service-1"
    assert body["unitsUsed"] == 1.0
    iso = datetime.fromtimestamp(1700000000, tz=timezone.utc).isoformat().replace("+00:00", "Z")
    assert body["timestamp"] == iso
    assert req.headers.get("X-AgentVend-Timestamp") == "1700000000"
    body_str = json.dumps(body, separators=(",", ":"))
    assert req.headers.get("X-AgentVend-Signature") == calculate_hmac_with_timestamp(
        body_str, "1700000000", SERVICE_SECRET
    )


@responses.activate
def test_report_usage_custom_usage_path_prefix():
    responses.add(
        responses.POST,
        f"{USAGE_BASE}/usage/api/v1/report",
        json={
            "status": "ok",
            "isOverLimit": False,
            "remainingRequestsPerPeriod": 1,
        },
        status=200,
    )

    result = report_usage_at(
        USAGE_BASE,
        "user-1",
        "service-1",
        1.0,
        SERVICE_SECRET,
        timestamp=1700000000.0,
        usage_path_prefix="/usage/api/v1",
    )

    assert result.status == "ok"
    assert responses.calls[0].request.url == f"{USAGE_BASE}/usage/api/v1/report"


@responses.activate
def test_report_usage_raises_on_5xx():
    """report_usage raises when the usage service returns 500."""
    responses.add(
        responses.POST,
        f"{USAGE_BASE}/api/usage/report",
        body="Internal error",
        status=500,
    )

    with pytest.raises(Exception):
        report_usage(USAGE_BASE, "user-1", "service-1", 1.0, SERVICE_SECRET)


@responses.activate
def test_report_progress_posts_to_progress_url_with_signature():
    """report_progress POSTs to the base URL from progress_url with signature headers."""
    progress_path = "/api/usage/progress/req-123"
    timestamp = "1700000000"
    progress_url = f"http://usage.test{progress_path}?signature=ignored&timestamp={timestamp}"

    responses.add(
        responses.POST,
        f"http://usage.test{progress_path}",
        status=200,
    )

    ok = report_progress(
        progress_url, "req-123", "processing", 50, SERVICE_SECRET
    )

    assert ok is True
    assert len(responses.calls) == 1
    req = responses.calls[0].request
    assert req.headers.get("X-AgentVend-Timestamp") == timestamp
    assert "X-AgentVend-Signature" in req.headers
    body = json.loads(req.body)
    assert body["stage"] == "processing"
    assert body["percentageComplete"] == 50


@responses.activate
def test_report_completion_posts_to_callback_url_with_signature():
    """report_completion POSTs to the base URL from callback_url with signature headers."""
    complete_path = "/api/usage/complete/req-456"
    timestamp = "1700000001"
    callback_url = f"http://usage.test{complete_path}?timestamp={timestamp}"

    responses.add(
        responses.POST,
        f"http://usage.test{complete_path}",
        status=200,
    )

    ok = report_completion_with_result(
        callback_url,
        "req-456",
        CompletionStatus.COMPLETED,
        SERVICE_SECRET,
        "done",
        units=1.0,
    )

    assert ok is True
    assert len(responses.calls) == 1
    req = responses.calls[0].request
    assert req.headers.get("X-AgentVend-Timestamp") == timestamp
    assert "X-AgentVend-Signature" in req.headers
    body = json.loads(req.body)
    assert body["status"] == "COMPLETED"
    assert body["result"] == "done"
    assert body["units"] == 1.0


def test_report_progress_returns_false_when_url_missing_timestamp():
    """report_progress returns False when the URL has no timestamp query param."""
    progress_url = f"{USAGE_BASE}/api/usage/progress/req-1"
    ok = report_progress(progress_url, "req-1", "stage", 0, SERVICE_SECRET)
    assert ok is False


def test_report_completion_returns_false_when_url_missing_timestamp():
    """report_completion returns False when the URL has no timestamp query param."""
    callback_url = f"{USAGE_BASE}/api/usage/complete/req-1"
    ok = report_completion(
        callback_url, "req-1", CompletionStatus.FAILED, SERVICE_SECRET
    )
    assert ok is False
