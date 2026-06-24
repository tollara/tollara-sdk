<?php

declare(strict_types=1);

namespace Tollara\AgentSdk;

final class UsageCallbackResult
{
    public function __construct(
        public bool $success,
        public int $httpStatus,
        public string $httpStatusText,
        public string $requestUrl,
        public ?string $responseBody = null,
        public ?string $networkError = null,
    ) {
    }
}
