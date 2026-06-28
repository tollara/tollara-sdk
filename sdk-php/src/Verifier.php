<?php

declare(strict_types=1);

namespace Tollara\AgentSdk;

final class InboundHmacRequest
{
    /**
     * @param list<string> $roles
     */
    public function __construct(
        public string $signature,
        public string $timestamp,
        public string $payload,
        public string $userId,
        public string $serviceProductId = '',
        public array $roles = [],
        public string $subscriptionStatus = '',
        public string $billingModelType = '',
        public string $measurementType = '',
        public string $unitLabel = '',
        public ?string $signingVersion = null,
        public string $plan = '',
        public string $quotaRemaining = '',
        public bool $subscriptionActive = false,
    ) {
    }
}

final class Verifier
{
    private const INVOKE_ELIGIBLE = ['ACTIVE', 'TRIAL', 'CANCELLING', 'CANCELLING_PENDING'];

    public static function grantsAccess(?string $subscriptionStatus): bool
    {
        if ($subscriptionStatus === null || trim($subscriptionStatus) === '') {
            return false;
        }
        return in_array(trim($subscriptionStatus), self::INVOKE_ELIGIBLE, true);
    }

    public static function buildGatewayUserContextString(
        string $userId,
        string $plan,
        array $roles,
        string $quotaRemaining,
        bool $subscriptionActive,
        string $billingModelType,
        string $measurementType,
        string $unitLabel
    ): string {
        return $userId . $plan . implode(',', $roles) . $quotaRemaining
            . ($subscriptionActive ? 'true' : 'false')
            . $billingModelType . $measurementType . $unitLabel;
    }

    /**
     * @param list<string> $roles
     */
    public static function buildGatewayUserContextStringV2(
        string $userId,
        string $plan,
        array $roles,
        bool $subscriptionActive,
        string $billingModelType,
        string $measurementType,
        string $unitLabel
    ): string {
        return '2' . $userId . $plan . implode(',', $roles)
            . ($subscriptionActive ? 'true' : 'false')
            . $billingModelType . $measurementType . $unitLabel;
    }

    /**
     * @param list<string> $roles
     */
    public static function buildGatewayUserContextStringV3(
        string $userId,
        string $serviceProductId,
        array $roles,
        string $subscriptionStatus,
        string $billingModelType,
        string $measurementType,
        string $unitLabel
    ): string {
        return '3' . $userId . $serviceProductId . implode(',', $roles)
            . $subscriptionStatus . $billingModelType . $measurementType . $unitLabel;
    }

    private static function headerGetCi(array $headers, string $canonical): string
    {
        foreach ($headers as $k => $v) {
            if (strcasecmp((string) $k, $canonical) === 0) {
                return is_string($v) ? $v : (string) $v;
            }
        }
        return '';
    }

    private static function formatQuota(string $raw): string
    {
        if ($raw === '') {
            return '';
        }
        if (is_numeric($raw)) {
            $f = (float) $raw;
            if ($f === (float) (int) $f) {
                return (string) (int) $f;
            }
        }
        return $raw;
    }

    private static function parseSubscriptionActive(string $raw): bool
    {
        return $raw === 'true' || $raw === '1';
    }

    public static function verifyInboundHmac(string $agentSecret, InboundHmacRequest $req): bool
    {
        if (trim((string) $req->signingVersion) === TollaraHeaders::SIGNING_VERSION_V3) {
            return self::verifySignatureV3(
                $agentSecret,
                $req->signature,
                $req->timestamp,
                $req->payload,
                $req->userId,
                $req->serviceProductId,
                $req->roles,
                $req->subscriptionStatus,
                $req->billingModelType,
                $req->measurementType,
                $req->unitLabel
            );
        }
        return self::verifySignature(
            $agentSecret,
            $req->signature,
            $req->timestamp,
            $req->payload,
            $req->userId,
            $req->plan,
            $req->roles,
            $req->quotaRemaining,
            $req->subscriptionActive,
            $req->billingModelType,
            $req->measurementType,
            $req->unitLabel,
            $req->signingVersion
        );
    }

    /**
     * @param array<string, string|null> $headers
     */
    public static function verifySignatureFromHeaders(string $agentSecret, array $headers, string $payload): bool
    {
        $sig = self::headerGetCi($headers, TollaraHeaders::SIGNATURE);
        $ts = self::headerGetCi($headers, TollaraHeaders::TIMESTAMP);
        if ($sig === '' || $ts === '') {
            return false;
        }
        $rolesCsv = self::headerGetCi($headers, TollaraHeaders::ROLES);
        $roles = $rolesCsv === '' ? [] : array_values(array_filter(array_map('trim', explode(',', $rolesCsv))));
        $quotaRaw = self::headerGetCi($headers, TollaraHeaders::QUOTA_REMAINING);
        $subRaw = self::headerGetCi($headers, TollaraHeaders::SUBSCRIPTION_ACTIVE);
        $bm = self::headerGetCi($headers, TollaraHeaders::BILLING_MODEL);
        $mt = self::headerGetCi($headers, TollaraHeaders::MEASUREMENT_TYPE);
        $ul = self::headerGetCi($headers, TollaraHeaders::UNIT_LABEL);
        $req = new InboundHmacRequest(
            $sig,
            $ts,
            $payload,
            self::headerGetCi($headers, TollaraHeaders::USER_ID),
            self::headerGetCi($headers, TollaraHeaders::SERVICE_PRODUCT_ID),
            $roles,
            self::headerGetCi($headers, TollaraHeaders::SUBSCRIPTION_STATUS),
            $bm,
            $mt,
            $ul,
            self::headerGetCi($headers, TollaraHeaders::SIGNING_VERSION) ?: null,
            self::headerGetCi($headers, TollaraHeaders::PLAN),
            self::formatQuota($quotaRaw),
            self::parseSubscriptionActive($subRaw)
        );
        return self::verifyInboundHmac($agentSecret, $req);
    }

    /**
     * @param array<string, string|null> $headers
     */
    public static function verifyInboundHmacAndGetUserContext(string $agentSecret, array $headers, string $payload): ?UserContext
    {
        if (!self::verifySignatureFromHeaders($agentSecret, $headers, $payload)) {
            return null;
        }
        return self::parseUserContext($headers);
    }

    /**
     * @param list<string> $roles
     */
    public static function verifySignatureV3(
        string $agentSecret,
        string $signature,
        string $timestamp,
        string $payload,
        string $userId,
        string $serviceProductId,
        array $roles,
        string $subscriptionStatus,
        string $billingModelType = '',
        string $measurementType = '',
        string $unitLabel = ''
    ): bool {
        if ($signature === '' || $timestamp === '' || $agentSecret === '') {
            return false;
        }
        $userContextString = self::buildGatewayUserContextStringV3(
            $userId,
            $serviceProductId,
            $roles,
            $subscriptionStatus,
            $billingModelType,
            $measurementType,
            $unitLabel
        );
        $dataToSign = $payload . $timestamp . $userContextString;
        $expected = Hmac::calculateHmac($dataToSign, $agentSecret);
        return Hmac::constantTimeEquals($expected, $signature);
    }

    /**
     * @param list<string> $roles
     */
    public static function verifySignature(
        string $agentSecret,
        string $signature,
        string $timestamp,
        string $payload,
        string $userId,
        string $plan,
        array $roles,
        string $quotaRemaining,
        bool $subscriptionActive,
        string $billingModelType = '',
        string $measurementType = '',
        string $unitLabel = '',
        ?string $signingVersion = null
    ): bool {
        if ($signature === '' || $timestamp === '' || $agentSecret === '') {
            return false;
        }
        if (trim((string) $signingVersion) === TollaraHeaders::SIGNING_VERSION_V3) {
            return false;
        }
        $isV2 = trim((string) $signingVersion) === '2';
        $userContextString = $isV2
            ? self::buildGatewayUserContextStringV2(
                $userId,
                $plan,
                $roles,
                $subscriptionActive,
                $billingModelType,
                $measurementType,
                $unitLabel
            )
            : self::buildGatewayUserContextString(
                $userId,
                $plan,
                $roles,
                $quotaRemaining,
                $subscriptionActive,
                $billingModelType,
                $measurementType,
                $unitLabel
            );
        $dataToSign = $payload . $timestamp . $userContextString;
        $expected = Hmac::calculateHmac($dataToSign, $agentSecret);
        return Hmac::constantTimeEquals($expected, $signature);
    }

    /**
     * @param array<string, string|null> $headers
     */
    public static function parseUserContext(array $headers): UserContext
    {
        $rolesCsv = self::headerGetCi($headers, TollaraHeaders::ROLES);
        $roles = $rolesCsv === '' ? [] : array_values(array_filter(array_map('trim', explode(',', $rolesCsv))));
        $qRaw = self::headerGetCi($headers, TollaraHeaders::QUOTA_REMAINING);
        $quota = $qRaw !== '' && is_numeric($qRaw) ? (float) $qRaw : null;
        $sub = self::headerGetCi($headers, TollaraHeaders::SUBSCRIPTION_ACTIVE);
        $subActive = self::parseSubscriptionActive($sub);
        $bm = self::headerGetCi($headers, TollaraHeaders::BILLING_MODEL);
        $mt = self::headerGetCi($headers, TollaraHeaders::MEASUREMENT_TYPE);
        $ul = self::headerGetCi($headers, TollaraHeaders::UNIT_LABEL);
        $sp = self::headerGetCi($headers, TollaraHeaders::SERVICE_PRODUCT_ID);
        $ss = self::headerGetCi($headers, TollaraHeaders::SUBSCRIPTION_STATUS);
        return new UserContext(
            self::headerGetCi($headers, TollaraHeaders::USER_ID),
            $sp,
            $roles,
            $ss,
            $bm,
            $mt,
            $ul,
            self::headerGetCi($headers, TollaraHeaders::PLAN),
            $quota,
            $subActive
        );
    }
}

final class UserContext
{
    /**
     * @param list<string> $roles
     */
    public function __construct(
        public string $userId,
        public string $serviceProductId,
        public array $roles,
        public string $subscriptionStatus,
        public string $billingModelType = '',
        public string $measurementType = '',
        public string $unitLabel = '',
        public string $plan = '',
        public ?float $quotaRemaining = null,
        public bool $subscriptionActive = false,
    ) {
    }
}
