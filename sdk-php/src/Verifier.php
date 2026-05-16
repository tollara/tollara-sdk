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
        public string $plan,
        public array $roles,
        public string $quotaRemaining,
        public bool $subscriptionActive = false,
        public string $billingModelType = '',
        public string $measurementType = '',
        public string $unitLabel = '',
    ) {
    }
}

final class Verifier
{
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
            $req->unitLabel
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
            self::headerGetCi($headers, TollaraHeaders::PLAN),
            $roles,
            self::formatQuota($quotaRaw),
            self::parseSubscriptionActive($subRaw),
            $bm,
            $mt,
            $ul
        );
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
            self::headerGetCi($headers, TollaraHeaders::SIGNING_VERSION)
        );
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
        return new UserContext(
            self::headerGetCi($headers, TollaraHeaders::USER_ID),
            self::headerGetCi($headers, TollaraHeaders::PLAN),
            $roles,
            $quota,
            $subActive,
            $bm,
            $mt,
            $ul
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
        public string $plan,
        public array $roles,
        public ?float $quotaRemaining,
        public bool $subscriptionActive,
        public string $billingModelType = '',
        public string $measurementType = '',
        public string $unitLabel = '',
    ) {
    }
}
