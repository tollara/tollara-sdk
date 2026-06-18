import type { IDataObject, INodeExecutionData } from 'n8n-workflow';

/**
 * Signed payload bytes for inbound gateway HMAC.
 * Prefer raw body from the Webhook node's binary output when "Raw Body" is enabled.
 */
function headerContentLength(headers: Record<string, string>): number | null {
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === 'content-length' && value != null && value !== '') {
      const n = Number(value);
      return Number.isNaN(n) ? null : n;
    }
  }
  return null;
}

function isEmptyBodyValue(body: unknown): boolean {
  if (body == null) return true;
  if (typeof body === 'string') return body.length === 0;
  if (typeof body === 'object' && !Array.isArray(body)) {
    return Object.keys(body as Record<string, unknown>).length === 0;
  }
  return false;
}

export function signedPayloadFromWebhookItem(
  item: INodeExecutionData,
  rawBodyBinaryProperty: string,
): string {
  const binary = item.binary?.[rawBodyBinaryProperty];
  if (binary?.data) {
    return Buffer.from(binary.data, 'base64').toString('utf8');
  }

  const json = item.json as IDataObject;
  const headers = headersFromWebhookItem(item);
  const contentLength = headerContentLength(headers);
  if (contentLength === 0) {
    return '';
  }

  const body = json.body;
  if (isEmptyBodyValue(body)) return '';
  if (typeof body === 'string') return body;
  return JSON.stringify(body);
}

export function headersFromWebhookItem(item: INodeExecutionData): Record<string, string> {
  const json = item.json as IDataObject;
  const headers = json.headers;
  if (headers != null && typeof headers === 'object' && !Array.isArray(headers)) {
    return headers as Record<string, string>;
  }
  return {};
}

/** Bearer token from Webhook node output (`headers.authorization`). */
export function bearerTokenFromWebhookItem(item: INodeExecutionData): string | undefined {
  const headers = headersFromWebhookItem(item);
  let authorization: string | undefined;
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === 'authorization' && typeof value === 'string') {
      authorization = value;
      break;
    }
  }
  if (!authorization) return undefined;
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  return (match ? match[1] : authorization).trim() || undefined;
}
