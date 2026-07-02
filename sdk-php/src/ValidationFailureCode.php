<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

/** Canonical failure codes for validate outcome (docs-sdk §2.1.1). */
final class ValidationFailureCode
{
    public const MISSING_KEY = 'MISSING_KEY';
    public const NETWORK = 'NETWORK';
    public const HTTP_ERROR = 'HTTP_ERROR';
    public const MISSING_SIGNATURE_HEADERS = 'MISSING_SIGNATURE_HEADERS';
    public const HMAC_MISMATCH = 'HMAC_MISMATCH';
    public const INVALID_KEY = 'INVALID_KEY';
    public const PARSE_ERROR = 'PARSE_ERROR';
}
