<?php

declare(strict_types=1);

namespace AgentVend\AgentSdk;

final class AgentVendHeaders
{
    public const SIGNATURE = 'X-AgentVend-Signature';
    public const TIMESTAMP = 'X-AgentVend-Timestamp';
    public const USER_ID = 'X-AgentVend-User-ID';
    public const PLAN = 'X-AgentVend-Plan';
    public const ROLES = 'X-AgentVend-Roles';
    public const QUOTA_REMAINING = 'X-AgentVend-Quota-Remaining';
    public const SUBSCRIPTION_ACTIVE = 'X-AgentVend-Subscription-Active';
}
