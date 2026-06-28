<?php

declare(strict_types=1);

namespace Tollara\AgentSdk;

final class TollaraHeaders
{
    public const SIGNATURE = 'X-Tollara-Signature';
    public const TIMESTAMP = 'X-Tollara-Timestamp';
    public const USER_ID = 'X-Tollara-User-ID';
    public const SERVICE_PRODUCT_ID = 'X-Tollara-Service-Product-ID';
    public const ROLES = 'X-Tollara-Roles';
    public const SUBSCRIPTION_STATUS = 'X-Tollara-Subscription-Status';
    public const BILLING_MODEL = 'X-Tollara-Billing-Model';
    public const MEASUREMENT_TYPE = 'X-Tollara-Measurement-Type';
    public const UNIT_LABEL = 'X-Tollara-Unit-Label';
    public const SIGNING_VERSION = 'X-Tollara-Signing-Version';
    public const SIGNING_VERSION_V3 = '3';

    /** @deprecated v1/v2 only */
    public const PLAN = 'X-Tollara-Plan';
    /** @deprecated v1 only */
    public const QUOTA_REMAINING = 'X-Tollara-Quota-Remaining';
    /** @deprecated v1/v2 only */
    public const SUBSCRIPTION_ACTIVE = 'X-Tollara-Subscription-Active';
}
