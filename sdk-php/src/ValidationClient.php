<?php

declare(strict_types=1);

namespace Tollara\ServiceSdk;

final class ValidationClient
{
    /**
     * @param array<string, mixed> $headers
     */
    public static function outcomeFromValidateResponse(
        int $httpStatus,
        string $responseText,
        array $headers,
        string $serviceSecret,
        ?string $fallbackServiceId,
    ): ServiceKeyValidationOutcome {
        if ($httpStatus < 200 || $httpStatus >= 300) {
            $unsignedInvalid = self::invalidKeyFromUnsignedErrorBody($responseText, $httpStatus);
            if ($unsignedInvalid !== null) {
                return ServiceKeyValidationOutcome::failure($unsignedInvalid);
            }
            return ServiceKeyValidationOutcome::failure(
                new ServiceKeyValidationFailure(ValidationFailureCode::HTTP_ERROR, null, $httpStatus),
            );
        }

        $sig = self::headerGetCi($headers, TollaraHeaders::SIGNATURE);
        $ts = self::headerGetCi($headers, TollaraHeaders::TIMESTAMP);
        if ($sig === '' || $ts === '') {
            return ServiceKeyValidationOutcome::failure(
                new ServiceKeyValidationFailure(ValidationFailureCode::MISSING_SIGNATURE_HEADERS, null, $httpStatus),
            );
        }
        if (!Hmac::validateHmacSignature($sig, $responseText . $ts, $serviceSecret)) {
            return ServiceKeyValidationOutcome::failure(
                new ServiceKeyValidationFailure(ValidationFailureCode::HMAC_MISMATCH, null, $httpStatus),
            );
        }

        $json = json_decode($responseText, true);
        if (!is_array($json)) {
            return ServiceKeyValidationOutcome::failure(
                new ServiceKeyValidationFailure(ValidationFailureCode::PARSE_ERROR, null, $httpStatus),
            );
        }
        if (array_key_exists('valid', $json) && $json['valid'] === false) {
            $message = isset($json['error']) ? (string) $json['error'] : null;
            return ServiceKeyValidationOutcome::failure(
                new ServiceKeyValidationFailure(ValidationFailureCode::INVALID_KEY, $message, $httpStatus),
            );
        }

        $result = ServiceKeyValidationResult::fromArray($json);
        if ($result->serviceId === null && $fallbackServiceId !== null && $fallbackServiceId !== '') {
            $result = new ServiceKeyValidationResult(
                userId: $result->userId,
                serviceId: $fallbackServiceId,
                serviceKeyId: $result->serviceKeyId,
                serviceProductId: $result->serviceProductId,
                roles: $result->roles,
                subscriptionStatus: $result->subscriptionStatus,
                validationSchemaVersion: $result->validationSchemaVersion,
                billingModelType: $result->billingModelType,
                measurementType: $result->measurementType,
                unitLabel: $result->unitLabel,
            );
        }

        return ServiceKeyValidationOutcome::success($result);
    }

    private static function invalidKeyFromUnsignedErrorBody(
        string $responseText,
        int $httpStatus,
    ): ?ServiceKeyValidationFailure {
        if (!in_array($httpStatus, [401, 403], true)) {
            return null;
        }
        $json = json_decode($responseText, true);
        if (!is_array($json) || !array_key_exists('valid', $json) || $json['valid'] !== false) {
            return null;
        }
        $message = isset($json['error']) ? (string) $json['error'] : null;

        return new ServiceKeyValidationFailure(ValidationFailureCode::INVALID_KEY, $message, $httpStatus);
    }

    /** @param array<string, mixed> $headers */
    private static function headerGetCi(array $headers, string $name): string
    {
        foreach ($headers as $k => $v) {
            if (strcasecmp((string) $k, $name) === 0) {
                return is_array($v) ? (string) ($v[0] ?? '') : (string) $v;
            }
        }

        return '';
    }
}
