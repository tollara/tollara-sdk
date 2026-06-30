import type { IDataObject, INodeExecutionData } from 'n8n-workflow';
import type { ServiceKeyValidationOutcome, ValidationFailureCode } from '@tollara/service-sdk';
import { passthroughItemWithJson, validationResultToUserContext } from './passthroughItem';

/** Auth-node denial when HMAC/key is valid but subscription status does not grant invoke access. */
export type TollaraAuthErrorCode = ValidationFailureCode | 'ACCESS_DENIED';

/** Core validate failures that indicate seller/infra misconfiguration or transient outage (not caller fault). */
const INFRA_VALIDATION_CODES = new Set<ValidationFailureCode>([
  'NETWORK',
  'HTTP_ERROR',
  'MISSING_SIGNATURE_HEADERS',
  'PARSE_ERROR',
]);

export function isInfraValidationFailure(code: ValidationFailureCode): boolean {
  return INFRA_VALIDATION_CODES.has(code);
}

export type TollaraOutcomeFields = {
  tollaraOk: boolean;
  tollaraErrorCode?: TollaraAuthErrorCode;
  tollaraErrorMessage?: string;
};

export function outcomeFieldsFromValidation(
  outcome: ServiceKeyValidationOutcome,
): TollaraOutcomeFields {
  if (outcome.ok) {
    return { tollaraOk: true };
  }
  return {
    tollaraOk: false,
    tollaraErrorCode: outcome.code,
    tollaraErrorMessage: outcome.message,
  };
}

export function hmacFailureFields(): TollaraOutcomeFields {
  return {
    tollaraOk: false,
    tollaraErrorCode: 'HMAC_MISMATCH',
    tollaraErrorMessage: 'Invalid HMAC signature',
  };
}

export function missingKeyFailureFields(message: string): TollaraOutcomeFields {
  return {
    tollaraOk: false,
    tollaraErrorCode: 'MISSING_KEY',
    tollaraErrorMessage: message,
  };
}

export function accessDeniedFields(subscriptionStatus: string | null | undefined): TollaraOutcomeFields {
  const status = subscriptionStatus?.trim() || 'unknown';
  return {
    tollaraOk: false,
    tollaraErrorCode: 'ACCESS_DENIED',
    tollaraErrorMessage: `Subscription status '${status}' does not grant access`,
  };
}

/** Build Success-output item after validate key. */
export function validateKeySuccessItem(
  item: INodeExecutionData,
  outcome: Extract<ServiceKeyValidationOutcome, { ok: true }>,
  itemIndex: number,
): INodeExecutionData {
  return passthroughItemWithJson(
    item,
    {
      tollaraOk: true,
      userContext: validationResultToUserContext(outcome.result),
    },
    itemIndex,
  );
}

/** Build Unauthorized-output item after validate key or verify request (auth/crypto failure). */
export function authFailureItem(
  item: INodeExecutionData,
  fields: TollaraOutcomeFields,
  itemIndex: number,
): INodeExecutionData {
  return passthroughItemWithJson(item, fields as IDataObject, itemIndex);
}

/** Build Forbidden-output item when signature/key is valid but subscription does not grant access. */
export function accessDeniedItem(
  item: INodeExecutionData,
  userContext: IDataObject,
  itemIndex: number,
): INodeExecutionData {
  return passthroughItemWithJson(
    item,
    {
      ...accessDeniedFields(userContext.subscriptionStatus as string | null | undefined),
      userContext,
    },
    itemIndex,
  );
}

export function invokeOk(result: { statusCode: number } | null): boolean {
  return result != null && result.statusCode >= 200 && result.statusCode < 300;
}
