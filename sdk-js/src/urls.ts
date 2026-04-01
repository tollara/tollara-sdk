export function trimTrailingSlashes(s: string): string {
  let t = s.trim();
  while (t.endsWith('/')) t = t.slice(0, -1);
  return t;
}

export function joinUrl(base: string, path: string | null | undefined): string {
  const b = trimTrailingSlashes(base);
  if (path == null || path === '') return b;
  const p = path.startsWith('/') ? path : `/${path}`;
  return b + p;
}

export function resolveBaseUrl(override: string | null | undefined, fallback: string): string {
  const t = (override ?? '').trim();
  return trimTrailingSlashes(t || fallback);
}
