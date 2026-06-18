import type { IDataObject } from 'n8n-workflow';

export function getTollaraCredentials(credentials: IDataObject): {
  apiUrl: string | undefined;
  serviceSecret: string;
} {
  const raw = (credentials.apiUrl as string | undefined)?.trim();
  return {
    apiUrl: raw || undefined,
    serviceSecret: credentials.serviceSecret as string,
  };
}
