import {
  DEFAULT_API_URL,
  DEFAULT_CORE_PATH_PREFIX,
  DEFAULT_GATEWAY_PATH_PREFIX,
  DEFAULT_USAGE_PATH_PREFIX,
  ECS_CORE_PATH_PREFIX,
  ECS_GATEWAY_PATH_PREFIX,
  ECS_USAGE_PATH_PREFIX,
} from './constants';
import { resolveBaseUrl } from './urls';

/** True for hosted Tollara API hosts (prod/PPE/branded *.api.tollara.ai). */
export function isHostedTollaraApiOrigin(origin: string): boolean {
  try {
    const host = new URL(origin).hostname.toLowerCase();
    if (host === 'api.tollara.ai' || host.endsWith('.api.tollara.ai')) {
      return true;
    }
    if (host === 'api.ppe.tollara.ai' || host.endsWith('.api.ppe.tollara.ai')) {
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

export function resolveGatewayPathPrefix(
  baseUrl?: string | null,
  override?: string | null,
): string {
  const explicit = override?.trim();
  if (explicit) {
    return explicit;
  }
  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  return isHostedTollaraApiOrigin(origin) ? ECS_GATEWAY_PATH_PREFIX : DEFAULT_GATEWAY_PATH_PREFIX;
}

export function resolveCorePathPrefix(baseUrl?: string | null, override?: string | null): string {
  const explicit = override?.trim();
  if (explicit) {
    return explicit;
  }
  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  return isHostedTollaraApiOrigin(origin) ? ECS_CORE_PATH_PREFIX : DEFAULT_CORE_PATH_PREFIX;
}

export function resolveUsagePathPrefix(baseUrl?: string | null, override?: string | null): string {
  const explicit = override?.trim();
  if (explicit) {
    return explicit;
  }
  const origin = resolveBaseUrl(baseUrl, DEFAULT_API_URL);
  return isHostedTollaraApiOrigin(origin) ? ECS_USAGE_PATH_PREFIX : DEFAULT_USAGE_PATH_PREFIX;
}
