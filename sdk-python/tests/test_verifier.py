"""Tests for inbound HMAC verification helpers."""

from agentvend_agent_sdk import (
    AgentVendHeaders,
    InboundHmacRequest,
    SignedUserContext,
    calculate_hmac,
    get_user_context,
    verify_inbound_hmac,
    verify_signature,
    verify_signature_from_headers,
)


def test_verify_inbound_hmac_hmac_spec_vector():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    user_context_string = "user1plan1role1,role210"
    data_to_sign = payload + timestamp + user_context_string
    signature = calculate_hmac(data_to_sign, secret)
    req = InboundHmacRequest(
        signature=signature,
        timestamp=timestamp,
        payload=payload,
        signed_user_context=SignedUserContext(
            user_id="user1",
            plan="plan1",
            roles=["role1", "role2"],
            quota_remaining=10.0,
        ),
    )
    assert verify_inbound_hmac(secret, req) is True


def test_verify_signature_from_headers_lowercase_keys():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    user_context_string = "user1plan1role1,role210"
    data_to_sign = payload + timestamp + user_context_string
    signature = calculate_hmac(data_to_sign, secret)
    headers = {
        "x-agentvend-signature": signature,
        "x-agentvend-timestamp": timestamp,
        "x-agentvend-user-id": "user1",
        "x-agentvend-plan": "plan1",
        "x-agentvend-roles": "role1,role2",
        "x-agentvend-quota-remaining": "10",
    }
    assert verify_signature_from_headers(secret, headers, payload) is True


def test_get_user_context_case_insensitive():
    ctx = get_user_context(
        {
            "x-agentvend-user-id": "u1",
            AgentVendHeaders.SUBSCRIPTION_ACTIVE: "true",
        }
    )
    assert ctx.user_id == "u1"
    assert ctx.subscription_active is True


def test_legacy_verify_signature_still_works():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    sig = calculate_hmac(payload + timestamp + "user1plan1role1,role210", secret)
    assert verify_signature(secret, sig, timestamp, payload, "user1", "plan1", ["role1", "role2"], 10.0) is True
