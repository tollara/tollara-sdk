/**
 * Rewrites gateway-issued progress/complete URLs to a local usage service origin.
 * Preserves path and query (including signature/timestamp from the gateway).
 */
export function rewriteUsageServiceUrl(urlFromGateway: string, usageApiUrl: string | undefined): string {
  const usageOrigin = usageApiUrl?.trim().replace(/\/$/, '');
  if (!usageOrigin) {
    return urlFromGateway;
  }

  try {
    const from = new URL(urlFromGateway);
    const target = new URL(usageOrigin);
    from.protocol = target.protocol;
    from.host = target.host;
    return from.toString();
  } catch {
    return urlFromGateway;
  }
}
