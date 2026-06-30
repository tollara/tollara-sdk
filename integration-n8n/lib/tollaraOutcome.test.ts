import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  accessDeniedFields,
  hmacFailureFields,
  invokeOk,
  isInfraValidationFailure,
  missingKeyFailureFields,
  outcomeFieldsFromValidation,
} from './tollaraOutcome';

describe('tollaraOutcome', () => {
  it('maps validation failure outcome', () => {
    assert.deepEqual(
      outcomeFieldsFromValidation({ ok: false, code: 'INVALID_KEY', message: 'Key expired', httpStatus: 200 }),
      {
        tollaraOk: false,
        tollaraErrorCode: 'INVALID_KEY',
        tollaraErrorMessage: 'Key expired',
      },
    );
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
    assert.match(accessDeniedFields('EXPIRED').tollaraErrorMessage ?? '', /EXPIRED/);
  });

  it('classifies infra validation failures', () => {
    assert.equal(isInfraValidationFailure('NETWORK'), true);
    assert.equal(isInfraValidationFailure('HTTP_ERROR'), true);
    assert.equal(isInfraValidationFailure('INVALID_KEY'), false);
    assert.equal(isInfraValidationFailure('HMAC_MISMATCH'), false);
  });

  it('invokeOk checks 2xx', () => {
    assert.equal(invokeOk({ statusCode: 200 }), true);
    assert.equal(invokeOk({ statusCode: 401 }), false);
    assert.equal(invokeOk(null), false);
  });
});
