import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  accessDeniedFields,
  hmacFailureFields,
  invokeOk,
  isInfraValidationFailure,
  isSellerSecretValidationFailure,
  isValidateKeyErrorOutcome,
  missingKeyFailureFields,
  outcomeFieldsFromValidation,
  suggestedHttpStatusForAuthError,
} from './tollaraOutcome';

describe('tollaraOutcome', () => {
  it('maps validation failure outcome', () => {
    assert.deepEqual(
      outcomeFieldsFromValidation({ ok: false, code: 'INVALID_KEY', message: 'Key expired', httpStatus: 200 }),
      {
        tollaraOk: false,
        tollaraErrorCode: 'INVALID_KEY',
        tollaraErrorMessage: 'Key expired',
        tollaraHttpStatus: 401,
      },
    );
  });

  it('maps infra validation failure to 503', () => {
    assert.equal(
      outcomeFieldsFromValidation({ ok: false, code: 'NETWORK' }).tollaraHttpStatus,
      503,
    );
    assert.equal(
      outcomeFieldsFromValidation({ ok: false, code: 'HTTP_ERROR', httpStatus: 502 }).tollaraHttpStatus,
      502,
    );
  });

  it('suggestedHttpStatusForAuthError', () => {
    assert.equal(suggestedHttpStatusForAuthError('ACCESS_DENIED'), 403);
    assert.equal(suggestedHttpStatusForAuthError('HMAC_MISMATCH'), 401);
    assert.equal(suggestedHttpStatusForAuthError('NETWORK'), 503);
  });

  it('maps validation success outcome', () => {
    assert.deepEqual(outcomeFieldsFromValidation({ ok: true, result: {} as never }), { tollaraOk: true });
  });

  it('hmac failure uses HMAC_MISMATCH', () => {
    assert.equal(hmacFailureFields().tollaraErrorCode, 'HMAC_MISMATCH');
  });

  it('missing key uses MISSING_KEY', () => {
    assert.equal(missingKeyFailureFields('no bearer').tollaraErrorCode, 'MISSING_KEY');
  });

  it('access denied uses ACCESS_DENIED', () => {
    assert.equal(accessDeniedFields('EXPIRED').tollaraErrorCode, 'ACCESS_DENIED');
    assert.equal(accessDeniedFields('EXPIRED').tollaraHttpStatus, 403);
    assert.match(accessDeniedFields('EXPIRED').tollaraErrorMessage ?? '', /EXPIRED/);
  });

  it('classifies infra validation failures', () => {
    assert.equal(isInfraValidationFailure('NETWORK'), true);
    assert.equal(isInfraValidationFailure('HTTP_ERROR'), true);
    assert.equal(isInfraValidationFailure('INVALID_KEY'), false);
    assert.equal(isInfraValidationFailure('HMAC_MISMATCH'), false);
  });

  it('detects seller secret misconfiguration from Core error message', () => {
    assert.equal(isSellerSecretValidationFailure('Invalid agent_secret'), true);
    assert.equal(isSellerSecretValidationFailure('Invalid service key'), false);
  });

  it('routes validate key outcomes to Error vs Denied', () => {
    assert.equal(isValidateKeyErrorOutcome('HTTP_ERROR', undefined), true);
    assert.equal(isValidateKeyErrorOutcome('INVALID_KEY', 'Invalid service key'), false);
    assert.equal(isValidateKeyErrorOutcome('INVALID_KEY', 'Invalid agent_secret'), true);
    assert.equal(isValidateKeyErrorOutcome('MISSING_KEY', undefined), false);
  });

  it('invokeOk checks 2xx', () => {
    assert.equal(invokeOk({ statusCode: 200 }), true);
    assert.equal(invokeOk({ statusCode: 401 }), false);
    assert.equal(invokeOk(null), false);
  });
});
