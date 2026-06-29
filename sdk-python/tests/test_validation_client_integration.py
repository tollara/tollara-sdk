"""
Integration tests for the validation client against a mocked Core API.
Uses the 'responses' library to mock HTTP; see docs/sdk-api-spec.md §2.
"""
import json
from uuid import UUID

import pytest
import responses

from tollara_service_sdk.validation_client import (
    validate_service_key,
    validate_service_key_with_outcome,
    estimate_usage,
    ServiceKeyValidationResult,
    ServiceKeyValidationFailure,
    ValidationFailureCode,
    UsageEstimateResult,
)
from tollara_service_sdk.hmac_utils import calculate_hmac

CORE_BASE = "http://core.test"
SERVICE_SECRET = "test-agent-secret"
SERVICE_ID = "550e8400-e29b-41d4-a716-446655440000"
SERVICE_KEY_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
SERVICE_PRODUCT_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7"


@responses.activate
def test_validate_service_key_returns_result_when_core_returns_200_with_valid_hmac_v3():
    """Core returns 200 with valid HMAC headers; SDK returns parsed v3 result."""
    response_body = {
        "valid": True,
        "serviceKeyId": SERVICE_KEY_ID,
        "userId": "user-123",
        "serviceId": SERVICE_ID,
        "serviceProductId": SERVICE_PRODUCT_ID,
        "roles": ["user"],
        "subscriptionStatus": "ACTIVE",
        "billingModelType": "SUBSCRIPTION",
        "measurementType": "PER_REQUEST",
        "unitLabel": "request",
        "timestamp": 1700000000,
        "error": None,
        "validationSchemaVersion": 3,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    canonical = body_str + timestamp
    signature = calculate_hmac(canonical, SERVICE_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": signature,
            "X-Tollara-Timestamp": timestamp,
        },
    )

    result = validate_service_key(CORE_BASE, "bearer-token-xyz", SERVICE_SECRET, service_id=SERVICE_ID)

    assert result is not None
    assert isinstance(result, ServiceKeyValidationResult)
    assert result.user_id == "user-123"
    assert result.service_id == SERVICE_ID
    assert result.service_key_id == UUID(SERVICE_KEY_ID)
    assert result.service_product_id == SERVICE_PRODUCT_ID
    assert result.subscription_status == "ACTIVE"
    assert result.validation_schema_version == 3
    assert result.roles == ["user"]
    assert result.grant_access() is True
    assert result.billing_model_type == "SUBSCRIPTION"


@responses.activate
def test_validate_service_key_returns_none_when_core_returns_401():
    """Core returns 401; SDK returns None."""
    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        json={"valid": False, "error": "Invalid key"},
        status=401,
        headers={"Content-Type": "application/json"},
    )

    result = validate_service_key(CORE_BASE, "bad-key", SERVICE_SECRET, service_id=SERVICE_ID)

    assert result is None


@responses.activate
def test_validate_service_key_returns_none_when_hmac_invalid():
    """Core returns 200 but signature does not match; SDK returns None."""
    response_body = {
        "valid": True,
        "userId": "user-123",
        "serviceId": SERVICE_ID,
        "serviceProductId": SERVICE_PRODUCT_ID,
        "roles": [],
        "subscriptionStatus": "ACTIVE",
        "timestamp": 1700000000,
        "validationSchemaVersion": 3,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": "invalid-signature",
            "X-Tollara-Timestamp": "1700000000",
        },
    )

    result = validate_service_key(CORE_BASE, "bearer-token", SERVICE_SECRET, service_id=SERVICE_ID)

    assert result is None


def test_validate_service_key_with_outcome_missing_key():
    outcome = validate_service_key_with_outcome(CORE_BASE, "   ", SERVICE_SECRET, service_id=SERVICE_ID)
    assert isinstance(outcome, ServiceKeyValidationFailure)
    assert outcome.code == ValidationFailureCode.MISSING_KEY


@responses.activate
def test_validate_service_key_with_outcome_invalid_key_preserves_message():
    response_body = {"valid": False, "error": "Key expired"}
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    signature = calculate_hmac(body_str + timestamp, SERVICE_SECRET)
    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "X-Tollara-Signature": signature,
            "X-Tollara-Timestamp": timestamp,
        },
    )
    outcome = validate_service_key_with_outcome(CORE_BASE, "expired", SERVICE_SECRET, service_id=SERVICE_ID)
    assert isinstance(outcome, ServiceKeyValidationFailure)
    assert outcome.code == ValidationFailureCode.INVALID_KEY
    assert outcome.message == "Key expired"


@responses.activate
def test_validate_service_key_with_outcome_http_error_on_401():
    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body="unauthorized",
        status=401,
    )
    outcome = validate_service_key_with_outcome(CORE_BASE, "bad", SERVICE_SECRET, service_id=SERVICE_ID)
    assert isinstance(outcome, ServiceKeyValidationFailure)
    assert outcome.code == ValidationFailureCode.HTTP_ERROR
    assert outcome.http_status == 401


@responses.activate
def test_validate_service_key_with_outcome_hmac_mismatch():
    response_body = {"valid": True, "userId": "u1"}
    body_str = json.dumps(response_body, separators=(",", ":"))
    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "X-Tollara-Signature": "bad-signature",
            "X-Tollara-Timestamp": "1700000000",
        },
    )
    outcome = validate_service_key_with_outcome(CORE_BASE, "k", SERVICE_SECRET, service_id=SERVICE_ID)
    assert isinstance(outcome, ServiceKeyValidationFailure)
    assert outcome.code == ValidationFailureCode.HMAC_MISMATCH
    assert outcome.http_status == 200


def test_validate_service_key_with_outcome_network_error():
    outcome = validate_service_key_with_outcome(
        "http://127.0.0.1:1", "k", SERVICE_SECRET, service_id=SERVICE_ID
    )
    assert isinstance(outcome, ServiceKeyValidationFailure)
    assert outcome.code == ValidationFailureCode.NETWORK


@responses.activate
def test_validate_service_key_returns_none_when_valid_false_in_body():
    """Core returns 200 with valid HMAC but valid: false; SDK returns None."""
    response_body = {
        "valid": False,
        "userId": None,
        "serviceId": None,
        "serviceProductId": None,
        "roles": [],
        "subscriptionStatus": None,
        "billingModelType": None,
        "measurementType": None,
        "unitLabel": None,
        "timestamp": 1700000000,
        "error": "Key expired",
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    signature = calculate_hmac(body_str + timestamp, SERVICE_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": signature,
            "X-Tollara-Timestamp": timestamp,
        },
    )

    result = validate_service_key(CORE_BASE, "expired-key", SERVICE_SECRET, service_id=SERVICE_ID)

    assert result is None


@responses.activate
def test_validate_service_key_grant_access_false_for_expired_status():
    response_body = {
        "valid": True,
        "serviceKeyId": SERVICE_KEY_ID,
        "userId": "user-123",
        "serviceId": SERVICE_ID,
        "serviceProductId": SERVICE_PRODUCT_ID,
        "roles": [],
        "subscriptionStatus": "EXPIRED",
        "billingModelType": None,
        "measurementType": None,
        "unitLabel": None,
        "timestamp": 1700000000,
        "error": None,
        "validationSchemaVersion": 3,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    signature = calculate_hmac(body_str + timestamp, SERVICE_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": signature,
            "X-Tollara-Timestamp": timestamp,
        },
    )

    result = validate_service_key(CORE_BASE, "expired-key", SERVICE_SECRET, service_id=SERVICE_ID)

    assert result is not None
    assert result.grant_access() is False
    assert ServiceKeyValidationResult.grant_access_for_status("EXPIRED") is False


@responses.activate
def test_validate_service_key_sends_service_key_and_service_id_in_body():
    """Client sends correct JSON body with serviceKey and serviceId."""
    response_body = {
        "valid": True,
        "userId": "u",
        "serviceId": SERVICE_ID,
        "serviceProductId": SERVICE_PRODUCT_ID,
        "roles": [],
        "subscriptionStatus": "ACTIVE",
        "timestamp": 1700000000,
        "error": None,
        "validationSchemaVersion": 3,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    signature = calculate_hmac(body_str + "1700000000", SERVICE_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": signature,
            "X-Tollara-Timestamp": "1700000000",
        },
    )

    validate_service_key(CORE_BASE, "the-service-key", SERVICE_SECRET, service_id=SERVICE_ID)

    assert len(responses.calls) == 1
    req = responses.calls[0].request
    sent_body = json.loads(req.body)
    assert sent_body["serviceKey"] == "the-service-key"
    assert sent_body["serviceId"] == SERVICE_ID
    assert sent_body["serviceSecret"] == SERVICE_SECRET


@responses.activate
def test_validate_service_key_custom_core_path_prefix():
    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/validate",
        json={"valid": False},
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": "x",
            "X-Tollara-Timestamp": "1",
        },
    )
    validate_service_key(
        CORE_BASE,
        "the-service-key",
        SERVICE_SECRET,
        service_id=SERVICE_ID,
        core_path_prefix="/api/v1",
    )
    assert responses.calls[0].request.url == f"{CORE_BASE}/api/v1/service-keys/validate"


@responses.activate
def test_estimate_usage_returns_result_when_core_returns_200_with_valid_hmac():
    response_body = {
        "sufficientCredits": True,
        "wouldExceedCap": False,
        "wouldAllow": True,
        "estimatedCost": 0.1,
        "billingModelType": "SUBSCRIPTION",
        "measurementType": "PER_REQUEST",
        "unitLabel": "request",
        "breakdown": {
            "unitsRemaining": 199,
            "remainingSpendingCap": 20,
            "isOverLimit": False,
        },
        "estimateSchemaVersion": 3,
        "timestamp": 1700000000,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))
    timestamp = "1700000000"
    signature = calculate_hmac(body_str + timestamp, SERVICE_SECRET)

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/estimate-usage",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": signature,
            "X-Tollara-Timestamp": timestamp,
        },
    )

    result = estimate_usage(
        CORE_BASE, "key-1", SERVICE_SECRET, 1.5, service_id=SERVICE_ID
    )

    assert result is not None
    assert isinstance(result, UsageEstimateResult)
    assert result.http_status == 200
    assert result.would_allow is True
    assert result.sufficient_credits is True
    assert result.estimate_schema_version == 3
    assert result.timestamp == 1700000000
    assert result.billing_model_type == "SUBSCRIPTION"
    assert result.breakdown is not None
    assert result.breakdown.units_remaining == 199
    assert result.breakdown.remaining_spending_cap == 20
    assert result.breakdown.over_limit is False


@responses.activate
def test_estimate_usage_returns_none_when_hmac_invalid():
    response_body = {
        "sufficientCredits": False,
        "wouldExceedCap": True,
        "wouldAllow": False,
        "estimateSchemaVersion": 3,
        "timestamp": 1700000000,
    }
    body_str = json.dumps(response_body, separators=(",", ":"))

    responses.add(
        responses.POST,
        f"{CORE_BASE}/api/v1/service-keys/estimate-usage",
        body=body_str,
        status=200,
        headers={
            "Content-Type": "application/json",
            "X-Tollara-Signature": "bad",
            "X-Tollara-Timestamp": "1700000000",
        },
    )

    assert estimate_usage(CORE_BASE, "k", SERVICE_SECRET, 1.0, service_id=SERVICE_ID) is None


def test_estimate_usage_returns_none_when_units_not_positive():
    assert estimate_usage(CORE_BASE, "k", SERVICE_SECRET, 0, service_id=SERVICE_ID) is None
    assert estimate_usage(CORE_BASE, "k", SERVICE_SECRET, -1, service_id=SERVICE_ID) is None
