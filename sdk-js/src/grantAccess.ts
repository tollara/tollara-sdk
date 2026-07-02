const ACCESS_STATUSES = new Set(['ACTIVE', 'TRIAL', 'CANCELLING', 'CANCELLING_PENDING']);

/** Whether subscriptionStatus grants invoke access (validation/gateway v3). */
export function grantAccess(subscriptionStatus: string | null | undefined): boolean {
  if (subscriptionStatus == null || subscriptionStatus === '') return false;
  return ACCESS_STATUSES.has(subscriptionStatus.trim().toUpperCase());
}
