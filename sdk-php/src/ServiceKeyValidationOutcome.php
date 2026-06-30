<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class ServiceKeyValidationOutcome
{
    private function __construct(
        public bool $ok,
        public ?ServiceKeyValidationResult $result = null,
        public ?ServiceKeyValidationFailure $failure = null,
    ) {
    }

    public static function success(ServiceKeyValidationResult $result): self
    {
        return new self(true, $result, null);
    }

    public static function failure(ServiceKeyValidationFailure $failure): self
    {
        return new self(false, null, $failure);
    }
}
