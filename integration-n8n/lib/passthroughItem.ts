import type { IDataObject, INodeExecutionData } from 'n8n-workflow';
import type { ServiceKeyValidationResult } from '@tollara/service-sdk';

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

/** Map validateServiceKey result to the same userContext shape as Verify Request (v3). */
export function validationResultToUserContext(result: ServiceKeyValidationResult): IDataObject {
  return {
    userId: result.userId,
    serviceProductId: result.serviceProductId,
    roles: result.roles,
    subscriptionStatus: result.subscriptionStatus,
    grantsAccess: result.grantsAccess,
    billingModelType: result.billingModelType,
    measurementType: result.measurementType,
    unitLabel: result.unitLabel,
    serviceId: result.serviceId,
    serviceKeyId: result.serviceKeyId,
    validationSchemaVersion: result.validationSchemaVersion,
  };
}
