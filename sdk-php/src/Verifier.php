<?php

declare(strict_types=1);

namespace AgentVend\AgentSdk;

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
    ) {
    }
}

final class Verifier
{
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
            $req->quotaRemaining
        );
    }

    /**
     * @param array<string, string|null> $headers
     */
    public static function verifySignatureFromHeaders(string $agentSecret, array $headers, string $payload): bool
    {
        $sig = self::headerGetCi($headers, AgentVendHeaders::SIGNATURE);
        $ts = self::headerGetCi($headers, AgentVendHeaders::TIMESTAMP);
        if ($sig === '' || $ts === '') {
            return false;
        }
        $rolesCsv = self::headerGetCi($headers, AgentVendHeaders::ROLES);
        $roles = $rolesCsv === '' ? [] : array_values(array_filter(array_map('trim', explode(',', $rolesCsv))));
        $quotaRaw = self::headerGetCi($headers, AgentVendHeaders::QUOTA_REMAINING);
        $req = new InboundHmacRequest(
            $sig,
            $ts,
            $payload,
            self::headerGetCi($headers, AgentVendHeaders::USER_ID),
            self::headerGetCi($headers, AgentVendHeaders::PLAN),
            $roles,
            self::formatQuota($quotaRaw)
        );
        return self::verifyInboundHmac($agentSecret, $req);
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
        string $quotaRemaining
    ): bool {
        if ($signature === '' || $timestamp === '' || $agentSecret === '') {
            return false;
        }
        $userContextString = $userId . $plan . implode(',', $roles) . $quotaRemaining;
        $dataToSign = $payload . $timestamp . $userContextString;
        $expected = Hmac::calculateHmac($dataToSign, $agentSecret);
        return Hmac::constantTimeEquals($expected, $signature);
    }

    /**
     * @param array<string, string|null> $headers
     */
    public static function parseUserContext(array $headers): UserContext
    {
        $rolesCsv = self::headerGetCi($headers, AgentVendHeaders::ROLES);
        $roles = $rolesCsv === '' ? [] : array_values(array_filter(array_map('trim', explode(',', $rolesCsv))));
        $qRaw = self::headerGetCi($headers, AgentVendHeaders::QUOTA_REMAINING);
        $quota = $qRaw !== '' && is_numeric($qRaw) ? (float) $qRaw : null;
        $sub = self::headerGetCi($headers, AgentVendHeaders::SUBSCRIPTION_ACTIVE);
        $subActive = $sub === 'true' || $sub === '1';
        return new UserContext(
            self::headerGetCi($headers, AgentVendHeaders::USER_ID),
            self::headerGetCi($headers, AgentVendHeaders::PLAN),
            $roles,
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
        public string $plan,
        public array $roles,
        public ?float $quotaRemaining,
        public bool $subscriptionActive,
    ) {
    }
}
