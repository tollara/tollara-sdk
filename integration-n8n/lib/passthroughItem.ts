import type { IDataObject, INodeExecutionData } from 'n8n-workflow';
import type { ServiceKeyValidationResult, UserContext } from '@tollara/service-sdk';
import { grantAccess } from '@tollara/service-sdk';

/** Merge extra fields onto an item while preserving binary and pairedItem (Verify Request / Validate Key). */
export function passthroughItemWithJson(
  item: INodeExecutionData,
  extra: IDataObject,
  itemIndex: number,
): INodeExecutionData {
  return {
    json: { ...(item.json as IDataObject), ...extra },
    ...(item.binary ? { binary: item.binary } : {}),
    pairedItem: { item: itemIndex },
  };
}

/** Map verify-request user context to the same shape as Validate Key (v3). */
export function headerUserContextToPassthrough(ctx: UserContext): IDataObject {
  return {
    userId: ctx.userId,
    serviceProductId: ctx.serviceProductId,
    roles: ctx.roles,
    subscriptionStatus: ctx.subscriptionStatus,
    grantAccess: grantAccess(ctx.subscriptionStatus),
    billingModelType: ctx.billingModelType,
    measurementType: ctx.measurementType,
    unitLabel: ctx.unitLabel,
  };
}

/** Map validateServiceKey result to the same userContext shape as Verify Request (v3). */
export function validationResultToUserContext(result: ServiceKeyValidationResult): IDataObject {
  return {
    userId: result.userId,
    serviceProductId: result.serviceProductId,
    roles: result.roles,
    subscriptionStatus: result.subscriptionStatus,
    grantAccess: result.grantAccess,
    billingModelType: result.billingModelType,
    measurementType: result.measurementType,
    unitLabel: result.unitLabel,
    serviceId: result.serviceId,
    serviceKeyId: result.serviceKeyId,
    validationSchemaVersion: result.validationSchemaVersion,
  };
}
