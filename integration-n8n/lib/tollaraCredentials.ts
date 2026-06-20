import type { IDataObject } from 'n8n-workflow';

export interface TollaraCredentials {
  apiUrl: string | undefined;
  coreApiUrl: string | undefined;
  usageApiUrl: string | undefined;
  gatewayApiUrl: string | undefined;
  serviceSecret: string;
  serviceId: string | undefined;
}

function trimOptional(value: string | undefined): string | undefined {
  const raw = value?.trim();
  return raw || undefined;
}

export function getTollaraCredentials(credentials: IDataObject): TollaraCredentials {
  return {
    apiUrl: trimOptional(credentials.apiUrl as string | undefined),
    coreApiUrl: trimOptional(credentials.coreApiUrl as string | undefined),
    usageApiUrl: trimOptional(credentials.usageApiUrl as string | undefined),
    gatewayApiUrl: trimOptional(credentials.gatewayApiUrl as string | undefined),
    serviceSecret: credentials.serviceSecret as string,
    serviceId: trimOptional(credentials.serviceId as string | undefined),
  };
}

/** Service-specific override, else shared API URL (SDK defaults to production when both unset). */
export function resolveServiceApiUrl(
  serviceUrl: string | undefined,
  apiUrl: string | undefined,
): string | undefined {
  return serviceUrl ?? apiUrl;
}

export function resolveCoreApiUrl(credentials: TollaraCredentials): string | undefined {
  return resolveServiceApiUrl(credentials.coreApiUrl, credentials.apiUrl);
}

export function resolveUsageApiUrl(credentials: TollaraCredentials): string | undefined {
  return resolveServiceApiUrl(credentials.usageApiUrl, credentials.apiUrl);
}

export function resolveGatewayApiUrl(credentials: TollaraCredentials): string | undefined {
  return resolveServiceApiUrl(credentials.gatewayApiUrl, credentials.apiUrl);
}

/** Node parameter override, else credential default. */
export function resolveServiceId(credentialServiceId: string | undefined, nodeServiceId: string | undefined): string | null {
  const raw = (nodeServiceId?.trim() || credentialServiceId?.trim()) ?? '';
  return raw || null;
}
