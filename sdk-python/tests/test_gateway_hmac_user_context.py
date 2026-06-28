"""Golden tests for gateway HMAC user-context string builders (Java parity)."""

from tollara_service_sdk.verifier import (
    build_gateway_user_context_string,
    build_gateway_user_context_string_v2,
    build_gateway_user_context_string_v3,
)


def test_build_v3_all_fields_present_golden_string():
    ctx = build_gateway_user_context_string_v3(
        "sub-ext-id",
        "prod-uuid-1",
        ["roleA", "roleB"],
        "ACTIVE",
        "SUBSCRIPTION",
        "PER_REQUEST",
        "request",
    )
    assert ctx == "3sub-ext-idprod-uuid-1roleA,roleBACTIVESUBSCRIPTIONPER_REQUESTrequest"


def test_build_v3_empty_roles_golden_string():
    ctx = build_gateway_user_context_string_v3(
        "user-1",
        "prod-1",
        [],
        "TRIAL",
        None,
        None,
        None,
    )
    assert ctx == "3user-1prod-1TRIAL"


def test_build_v3_billing_fields_absent_golden_string():
    ctx = build_gateway_user_context_string_v3(
        "owner-id",
        "",
        None,
        "ACTIVE",
        None,
        None,
        None,
    )
    assert ctx == "3owner-idACTIVE"


def test_build_v3_non_access_status_golden_string():
    ctx = build_gateway_user_context_string_v3(
        "user-x",
        "prod-x",
        ["r1"],
        "EXPIRED",
        "PREPAID",
        "PER_REQUEST",
        "request",
    )
    assert ctx == "3user-xprod-xr1EXPIREDPREPAIDPER_REQUESTrequest"


def test_build_v2_owner_no_roles_golden_string():
    ctx = build_gateway_user_context_string_v2(
        "user-1",
        "owner",
        [],
        True,
        None,
        None,
        None,
    )
    assert ctx == "2user-1ownertrue"


def test_build_v2_subscriber_with_roles_and_billing_fields_golden_string():
    ctx = build_gateway_user_context_string_v2(
        "sub-ext-id",
        "pro",
        ["roleA", "roleB"],
        False,
        "SUBSCRIPTION",
        "PER_REQUEST",
        "request",
    )
    assert ctx == "2sub-ext-idproroleA,roleBfalseSUBSCRIPTIONPER_REQUESTrequest"


def test_build_legacy_v1_with_quota_golden_string():
    ctx = build_gateway_user_context_string(
        "a",
        "b",
        ["x"],
        5,
        False,
        "S",
        "M",
        "U",
    )
    assert ctx == "abx5falseSMU"
