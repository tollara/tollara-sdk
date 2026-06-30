<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class ServiceKeyValidationFailure
{
    public function __construct(
        public string $code,
        public ?string $message = null,
        public ?int $httpStatus = null,
    ) {
    }
}
