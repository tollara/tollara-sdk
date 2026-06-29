import type { IDataObject, INodeExecutionData } from 'n8n-workflow';
import type { ServiceKeyValidationOutcome, ValidationFailureCode } from '@tollara/service-sdk';
import { passthroughItemWithJson, validationResultToUserContext } from './passthroughItem';

export type TollaraOutcomeFields = {
  tollaraOk: boolean;
  tollaraErrorCode?: ValidationFailureCode;
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

/** Build Failure-output item after validate key or verify request. */
export function authFailureItem(
  item: INodeExecutionData,
  fields: TollaraOutcomeFields,
  itemIndex: number,
): INodeExecutionData {
  return passthroughItemWithJson(item, fields as IDataObject, itemIndex);
}

export function invokeOk(result: { statusCode: number } | null): boolean {
  return result != null && result.statusCode >= 200 && result.statusCode < 300;
}
