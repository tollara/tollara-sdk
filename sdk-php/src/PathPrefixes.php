<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class PathPrefixes
{
    public const ECS_CORE_PATH_PREFIX = '/core/api/v1';
    public const ECS_GATEWAY_PATH_PREFIX = '/gateway/api/v1';
    public const ECS_USAGE_PATH_PREFIX = '/usage/api/v1';

    public static function isHostedTollaraApiOrigin(string $origin): bool
    {
        $host = parse_url(trim($origin), PHP_URL_HOST);
        if (!is_string($host) || $host === '') {
            return false;
        }
        $host = strtolower($host);
        if ($host === 'api.tollara.ai' || str_ends_with($host, '.api.tollara.ai')) {
            return true;
        }
        return $host === 'api.ppe.tollara.ai' || str_ends_with($host, '.api.ppe.tollara.ai');
    }

    public static function resolveGatewayPathPrefix(?string $baseUrl, ?string $override = null): string
    {
        if ($override !== null && trim($override) !== '') {
            return trim($override);
        }
        $origin = self::resolveOrigin($baseUrl);
        return self::isHostedTollaraApiOrigin($origin)
            ? self::ECS_GATEWAY_PATH_PREFIX
            : TollaraClient::DEFAULT_GATEWAY_PATH_PREFIX;
    }

    public static function resolveCorePathPrefix(?string $baseUrl, ?string $override = null): string
    {
        if ($override !== null && trim($override) !== '') {
            return trim($override);
        }
        $origin = self::resolveOrigin($baseUrl);
        return self::isHostedTollaraApiOrigin($origin)
            ? self::ECS_CORE_PATH_PREFIX
            : TollaraClient::DEFAULT_CORE_PATH_PREFIX;
    }

    public static function resolveUsagePathPrefix(?string $baseUrl, ?string $override = null): string
    {
        if ($override !== null && trim($override) !== '') {
            return trim($override);
        }
        $origin = self::resolveOrigin($baseUrl);
        return self::isHostedTollaraApiOrigin($origin)
            ? self::ECS_USAGE_PATH_PREFIX
            : TollaraClient::DEFAULT_USAGE_PATH_PREFIX;
    }

    private static function resolveOrigin(?string $baseUrl): string
    {
        if ($baseUrl !== null && trim($baseUrl) !== '') {
            return rtrim(trim($baseUrl), '/');
        }
        return TollaraClient::DEFAULT_API_URL;
    }
}
