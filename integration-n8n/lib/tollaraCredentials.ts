import type { IDataObject, IExecuteFunctions } from 'n8n-workflow';

export interface TollaraCredentials {
  coreApiUrl: string | undefined;
  usageApiUrl: string | undefined;
  gatewayApiUrl: string | undefined;
}

function trimOptional(value: string | undefined): string | undefined {
  const raw = value?.trim();
  return raw || undefined;
}

export function getTollaraCredentials(credentials: IDataObject): TollaraCredentials {
  return {
    coreApiUrl: trimOptional(credentials.coreApiUrl as string | undefined),
    usageApiUrl: trimOptional(credentials.usageApiUrl as string | undefined),
    gatewayApiUrl: trimOptional(credentials.gatewayApiUrl as string | undefined),
  };
}

/** Read optional endpoint overrides from node parameters (production defaults when toggle is off). */
export function tollaraCredentialsFromNodeParameters(
  executeFunctions: IExecuteFunctions,
  itemIndex = 0,
): TollaraCredentials {
  const setApiEndpoints = executeFunctions.getNodeParameter('setApiEndpoints', itemIndex, false) as boolean;
  if (!setApiEndpoints) {
    return getTollaraCredentials({});
  }

  const params = executeFunctions.getNode().parameters;
  return getTollaraCredentials({
    coreApiUrl: params.coreApiUrl as string | undefined,
    usageApiUrl: params.usageApiUrl as string | undefined,
    gatewayApiUrl: params.gatewayApiUrl as string | undefined,
  });
}

export function resolveCoreApiUrl(credentials: TollaraCredentials): string | undefined {
  return credentials.coreApiUrl;
}

export function resolveUsageApiUrl(credentials: TollaraCredentials): string | undefined {
  return credentials.usageApiUrl;
}

export function resolveGatewayApiUrl(credentials: TollaraCredentials): string | undefined {
  return credentials.gatewayApiUrl;
}

type ApiUrlField = 'coreApiUrl' | 'usageApiUrl' | 'gatewayApiUrl';

const API_URL_LABELS: Record<ApiUrlField, string> = {
  coreApiUrl: 'Core API URL',
  usageApiUrl: 'Usage API URL',
  gatewayApiUrl: 'Gateway API URL',
};

/**
 * Throws when Set API Endpoints is enabled but the required URL for this node is blank.
 * Static seller misconfiguration — fail fast like requireServiceSecret (not a caller-facing Error branch).
 */
export function requireApiUrlWhenEndpointsEnabled(
  executeFunctions: IExecuteFunctions,
  field: ApiUrlField,
  itemIndex = 0,
): void {
  const setApiEndpoints = executeFunctions.getNodeParameter('setApiEndpoints', itemIndex, false) as boolean;
  if (!setApiEndpoints) {
    return;
  }
  const creds = tollaraCredentialsFromNodeParameters(executeFunctions, itemIndex);
  const url = creds[field];
  if (!url) {
    throw new Error(
      `${API_URL_LABELS[field]} is required when Set API Endpoints is enabled — set it on this node`,
    );
  }
}

export function requireCoreApiUrlWhenEndpointsEnabled(
  executeFunctions: IExecuteFunctions,
  itemIndex = 0,
): void {
  requireApiUrlWhenEndpointsEnabled(executeFunctions, 'coreApiUrl', itemIndex);
}

export function requireUsageApiUrlWhenEndpointsEnabled(
  executeFunctions: IExecuteFunctions,
  itemIndex = 0,
): void {
  requireApiUrlWhenEndpointsEnabled(executeFunctions, 'usageApiUrl', itemIndex);
}

export function requireGatewayApiUrlWhenEndpointsEnabled(
  executeFunctions: IExecuteFunctions,
  itemIndex = 0,
): void {
  requireApiUrlWhenEndpointsEnabled(executeFunctions, 'gatewayApiUrl', itemIndex);
}

export function requireServiceSecret(nodeServiceSecret: string | undefined): string {
  const secret = nodeServiceSecret?.trim() ?? '';
  if (!secret) {
    throw new Error('Service secret is required — set it on this node');
  }
  return secret;
}

export function requireServiceId(nodeServiceId: string | undefined): string {
  const serviceId = nodeServiceId?.trim() ?? '';
  if (!serviceId) {
    throw new Error('Service ID is required — set it on this node');
  }
  return serviceId;
}

const IMPORT_PLACEHOLDER_SERVICE_ID = 'YOUR_SERVICE_ID';

/** Empty, whitespace, or import placeholder → null so Core can infer service ID from the service key. */
export function optionalServiceId(nodeServiceId: string | undefined): string | null {
  const serviceId = nodeServiceId?.trim() ?? '';
  if (!serviceId || serviceId === IMPORT_PLACEHOLDER_SERVICE_ID) {
    return null;
  }
  return serviceId;
}
