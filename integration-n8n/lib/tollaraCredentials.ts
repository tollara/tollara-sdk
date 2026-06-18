import type { IDataObject } from 'n8n-workflow';

export function getTollaraCredentials(credentials: IDataObject): {
  apiUrl: string | undefined;
  serviceSecret: string;
  serviceId: string | undefined;
} {
  const rawApiUrl = (credentials.apiUrl as string | undefined)?.trim();
  const rawServiceId = (credentials.serviceId as string | undefined)?.trim();
  return {
    apiUrl: rawApiUrl || undefined,
    serviceSecret: credentials.serviceSecret as string,
    serviceId: rawServiceId || undefined,
  };
}

/** Node parameter override, else credential default. */
export function resolveServiceId(credentialServiceId: string | undefined, nodeServiceId: string | undefined): string | null {
  const raw = (nodeServiceId?.trim() || credentialServiceId?.trim()) ?? '';
  return raw || null;
}
