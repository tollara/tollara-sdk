"""Tests for inbound HMAC verification helpers."""

from tollara_service_sdk import (
    TollaraHeaders,
    InboundHmacRequest,
    SignedUserContext,
    calculate_hmac,
    get_user_context,
    grants_access,
    verify_inbound_context,
    verify_inbound_hmac,
    verify_signature,
    verify_signature_from_headers,
    verify_signature_from_headers_and_get_user_context,
)
from tollara_service_sdk.verifier import (
    build_gateway_user_context_string,
    build_gateway_user_context_string_v2,
    build_gateway_user_context_string_v3,
)


def test_verify_inbound_hmac_accepts_gateway_hmac_v3_when_signing_version_header_is_3():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string_v3(
        "user1",
        "prod-1",
        ["role1", "role2"],
        "ACTIVE",
        "SUBSCRIPTION",
        "PER_REQUEST",
        "request",
    )
    data_to_sign = payload + timestamp + ucs
    signature = calculate_hmac(data_to_sign, secret)
    headers = {
        "x-tollara-signature": signature,
        "x-tollara-timestamp": timestamp,
        "x-tollara-signing-version": "3",
        "x-tollara-user-id": "user1",
        "x-tollara-service-product-id": "prod-1",
        "x-tollara-roles": "role1,role2",
        "x-tollara-subscription-status": "ACTIVE",
        "x-tollara-billing-model": "SUBSCRIPTION",
        "x-tollara-measurement-type": "PER_REQUEST",
        "x-tollara-unit-label": "request",
    }
    assert verify_signature_from_headers(secret, headers, payload) is True
    ctx = verify_inbound_context(secret, headers, payload)
    assert ctx is not None
    assert ctx.service_product_id == "prod-1"
    assert ctx.subscription_status == "ACTIVE"
    assert grants_access(ctx.subscription_status) is True


def test_grants_access_returns_false_for_non_eligible_status():
    assert grants_access("EXPIRED") is False
    assert grants_access(None) is False
    assert grants_access("CANCELLING_PENDING") is True


def test_verify_inbound_hmac_accepts_gateway_hmac_v2_when_signing_version_header_is_2():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string_v2(
        "user1", "plan1", ["role1", "role2"], False, None, None, None
    )
    data_to_sign = payload + timestamp + ucs
    signature = calculate_hmac(data_to_sign, secret)
    headers = {
        "x-tollara-signature": signature,
        "x-tollara-timestamp": timestamp,
        "x-tollara-signing-version": "2",
        "x-tollara-user-id": "user1",
        "x-tollara-plan": "plan1",
        "x-tollara-roles": "role1,role2",
        "x-tollara-subscription-active": "false",
    }
    assert verify_signature_from_headers(secret, headers, payload) is True


def test_verify_inbound_hmac_rejects_v1_when_signature_is_v2_without_header():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string_v2(
        "user1", "plan1", ["role1", "role2"], False, None, None, None
    )
    signature = calculate_hmac(payload + timestamp + ucs, secret)
    headers = {
        "x-tollara-signature": signature,
        "x-tollara-timestamp": timestamp,
        "x-tollara-user-id": "user1",
        "x-tollara-plan": "plan1",
        "x-tollara-roles": "role1,role2",
        "x-tollara-subscription-active": "false",
    }
    assert verify_signature_from_headers(secret, headers, payload) is False


def test_verify_inbound_hmac_extended_vector():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string(
        "user1", "plan1", ["role1", "role2"], 10.0, False, None, None, None
    )
    data_to_sign = payload + timestamp + ucs
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
            subscription_active=False,
        ),
    )
    assert verify_inbound_hmac(secret, req) is True


def test_verify_signature_from_headers_lowercase_keys():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string(
        "user1", "plan1", ["role1", "role2"], 10.0, False, None, None, None
    )
    signature = calculate_hmac(payload + timestamp + ucs, secret)
    headers = {
        "x-tollara-signature": signature,
        "x-tollara-timestamp": timestamp,
        "x-tollara-user-id": "user1",
        "x-tollara-plan": "plan1",
        "x-tollara-roles": "role1,role2",
        "x-tollara-quota-remaining": "10",
        "x-tollara-subscription-active": "false",
    }
    assert verify_signature_from_headers(secret, headers, payload) is True


def test_verify_inbound_context_ok():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string(
        "user1", "plan1", ["role1", "role2"], 10.0, False, None, None, None
    )
    signature = calculate_hmac(payload + timestamp + ucs, secret)
    headers = {
        "x-tollara-signature": signature,
        "x-tollara-timestamp": timestamp,
        "x-tollara-user-id": "user1",
        "x-tollara-plan": "plan1",
        "x-tollara-roles": "role1,role2",
        "x-tollara-quota-remaining": "10",
        "x-tollara-subscription-active": "false",
    }
    ctx = verify_inbound_context(secret, headers, payload)
    assert ctx is not None
    assert ctx.user_id == "user1"
    assert ctx.plan == "plan1"
    assert verify_signature_from_headers_and_get_user_context is verify_inbound_context


def test_verify_inbound_context_invalid():
    headers = {
        "x-tollara-signature": "bad",
        "x-tollara-timestamp": "1700000000",
    }
    assert verify_inbound_context("my-agent-secret", headers, "") is None


def test_owner_like_gateway_vector():
    secret = "test-agent-secret"
    payload = '{"hello":1}'
    ts = "1700000000"
    ucs = build_gateway_user_context_string(
        "user-1", "owner", [], 9223372036854775807, True, None, None, None
    )
    sig = calculate_hmac(payload + ts + ucs, secret)
    assert (
        verify_signature(
            secret,
            sig,
            ts,
            payload,
            "user-1",
            "owner",
            [],
            9223372036854775807,
            True,
        )
        is True
    )


def test_subscriber_with_billing_headers():
    secret = "test-agent-secret"
    payload = ""
    ts = "1710000000"
    ucs = build_gateway_user_context_string(
        "sub-user",
        "basic",
        ["roleA", "roleB"],
        50.0,
        True,
        "SUBSCRIPTION",
        "PER_REQUEST",
        "request",
    )
    sig = calculate_hmac(payload + ts + ucs, secret)
    assert (
        verify_signature(
            secret,
            sig,
            ts,
            payload,
            "sub-user",
            "basic",
            ["roleA", "roleB"],
            50.0,
            True,
            "SUBSCRIPTION",
            "PER_REQUEST",
            "request",
        )
        is True
    )


def test_get_user_context_case_insensitive():
    ctx = get_user_context(
        {
            "x-tollara-user-id": "u1",
            TollaraHeaders.SERVICE_PRODUCT_ID: "prod-1",
            TollaraHeaders.SUBSCRIPTION_STATUS: "ACTIVE",
            TollaraHeaders.BILLING_MODEL: "USAGE_POSTPAID",
            TollaraHeaders.MEASUREMENT_TYPE: "PER_TOKEN",
            TollaraHeaders.UNIT_LABEL: "token",
        }
    )
    assert ctx.user_id == "u1"
    assert ctx.service_product_id == "prod-1"
    assert ctx.subscription_status == "ACTIVE"
    assert ctx.billing_model_type == "USAGE_POSTPAID"
    assert ctx.measurement_type == "PER_TOKEN"
    assert ctx.unit_label == "token"


def test_verify_signature_extended_explicit():
    secret = "my-agent-secret"
    payload = ""
    timestamp = "1700000000"
    ucs = build_gateway_user_context_string(
        "user1", "plan1", ["role1", "role2"], 10.0, False, None, None, None
    )
    sig = calculate_hmac(payload + timestamp + ucs, secret)
    assert (
        verify_signature(
            secret, sig, timestamp, payload, "user1", "plan1", ["role1", "role2"], 10.0, False
        )
        is True
    )
