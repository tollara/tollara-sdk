import type { IDataObject } from 'n8n-workflow';

export function parseJsonBody(body: string): IDataObject | string {
  const trimmed = body.trim();
  if (!trimmed) return {};
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as IDataObject;
    }
    return parsed as IDataObject;
  } catch {
    return trimmed;
  }
}
