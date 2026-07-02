/**
 * Re-exports @tollara/service-sdk for TypeScript. The published dist/lib/tollaraSdk.js
 * is replaced at build time with an esbuild bundle (no runtime npm dependency).
 */
export {
  CompletionStatus,
  estimateUsage,
  getRequestResult,
  getRequestStatus,
  getUserContext,
  grantAccess,
  invokeService,
  reportCompletion,
  reportProgress,
  reportUsage,
  validateServiceKeyWithOutcome,
  verifySignatureFromHeaders,
} from '@tollara/service-sdk';

export type {
  GatewayHttpMethod,
  ServiceKeyValidationOutcome,
  ServiceKeyValidationResult,
  UserContext,
  ValidationFailureCode,
} from '@tollara/service-sdk';
