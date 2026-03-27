"""
Integration tests for the validation client against a mocked Core API.
Uses the 'responses' library to mock HTTP; see docs/sdk-api-spec.md §2.
"""
import json

import pytest
import responses

from agentvend_agent_sdk.validation_client import validate_agent_key, AgentKeyValidationResult
from agentvend_agent_sdk.hmac_utils import calculate_hmac

CORE_BASE = "http://core.test/api/v1"
AGENT_SECRET = "test-agent-secret"
AGENT_ID = "550e8400-e29b-41d4-a716-446655440000"


@responses.activate
def test_validate_agent_key_returns_result_when_core_returns_200_with_valid_hmac():
    """Core returns 200 with valid HMAC headers; SDK returns parsed result."""
    response_body = {
        "valid": True,
        "userId": "user-123",
        "agentId": AGENT_ID,
        "plan": "basic",
        "roles": ["user"],
        "quotaRemaining": 100,
        "subscriptionActive": True,
        "timestamp": 1700000000,
        "error": None,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    canonical = body_str + timestamp
    signature = calculate_hmac(canonical, AGENT_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/agent-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-AgentVend-Signature": signature,
            "X-AgentVend-Timestamp": timestamp,
        },
    )

    result = validate_agent_key(CORE_BASE, "bearer-token-xyz", AGENT_SECRET, agent_id=AGENT_ID)

    assert result is not None
    assert isinstance(result, AgentKeyValidationResult)
    assert result.user_id == "user-123"
    assert result.agent_id == AGENT_ID
    assert result.plan == "basic"
    assert result.roles == ["user"]
    assert result.quota_remaining == 100
    assert result.subscription_active is True


@responses.activate
def test_validate_agent_key_returns_none_when_core_returns_401():
    """Core returns 401; SDK returns None."""
    responses.add(
        responses.POST,
        f"{CORE_BASE}/agent-keys/validate",
        json={"valid": False, "error": "Invalid key"},
        status=401,
        headers={"Content-Type": "application/json"},
    )

    result = validate_agent_key(CORE_BASE, "bad-key", AGENT_SECRET, agent_id=AGENT_ID)

    assert result is None


@responses.activate
def test_validate_agent_key_returns_none_when_hmac_invalid():
    """Core returns 200 but signature does not match; SDK returns None."""
    response_body = {
        "valid": True,
        "userId": "user-123",
        "agentId": AGENT_ID,
        "plan": "basic",
        "roles": [],
        "quotaRemaining": 100,
        "subscriptionActive": True,
        "timestamp": 1700000000,
        "error": None,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))

    responses.add(
        responses.POST,
        f"{CORE_BASE}/agent-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-AgentVend-Signature": "invalid-signature",
            "X-AgentVend-Timestamp": "1700000000",
        },
    )

    result = validate_agent_key(CORE_BASE, "bearer-token", AGENT_SECRET, agent_id=AGENT_ID)

    assert result is None


@responses.activate
def test_validate_agent_key_returns_none_when_valid_false_in_body():
    """Core returns 200 with valid HMAC but valid: false; SDK returns None."""
    response_body = {
        "valid": False,
        "userId": None,
        "agentId": AGENT_ID,
        "plan": None,
        "roles": [],
        "quotaRemaining": 0,
        "subscriptionActive": False,
        "timestamp": 1700000000,
        "error": "Key expired",
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    signature = calculate_hmac(body_str + timestamp, AGENT_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/agent-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-AgentVend-Signature": signature,
            "X-AgentVend-Timestamp": timestamp,
        },
    )

    result = validate_agent_key(CORE_BASE, "expired-key", AGENT_SECRET, agent_id=AGENT_ID)

    assert result is None


@responses.activate
def test_validate_agent_key_sends_agent_key_and_agent_id_in_body():
    """Client sends correct JSON body with agentKey and agentId."""
    response_body = {
        "valid": True,
        "userId": "u",
        "agentId": AGENT_ID,
        "plan": "basic",
        "roles": [],
        "quotaRemaining": 1,
        "subscriptionActive": True,
        "timestamp": 1700000000,
        "error": None,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    signature = calculate_hmac(body_str + "1700000000", AGENT_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/agent-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-AgentVend-Signature": signature,
            "X-AgentVend-Timestamp": "1700000000",
        },
    )

    validate_agent_key(CORE_BASE, "the-agent-key", AGENT_SECRET, agent_id=AGENT_ID)

    assert len(responses.calls) == 1
    req = responses.calls[0].request
    sent_body = json.loads(req.body)
    assert sent_body["agentKey"] == "the-agent-key"
    assert sent_body["agentId"] == AGENT_ID
    assert sent_body["agentSecret"] == AGENT_SECRET
