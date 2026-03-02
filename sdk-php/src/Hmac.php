<?php

declare(strict_types=1);

namespace Marketplace\AgentSdk;

final class Hmac
{
    public static function calculateHmac(string $data, string $key): string
    {
        $raw = hash_hmac('sha256', $data, $key, true);
        return base64_encode($raw);
    }

    public static function calculateHmacWithTimestamp(string $bodyString, string $timestamp, string $key): string
    {
        return self::calculateHmac($bodyString . $timestamp, $key);
    }

    public static function constantTimeEquals(?string $a, ?string $b): bool
    {
        if ($a === null || $b === null) {
            return $a === $b;
        }
        if (strlen($a) !== strlen($b)) {
            return false;
        }
        return hash_equals($a, $b);
    }

    public static function validateHmacSignature(string $signature, string $payloadString, string $key): bool
    {
        if ($signature === '' || $key === '') {
            return false;
        }
        $expected = self::calculateHmac($payloadString, $key);
        return self::constantTimeEquals($expected, $signature);
    }
}
