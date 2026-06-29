import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  hmacFailureFields,
  invokeOk,
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

  it('invokeOk checks 2xx', () => {
    assert.equal(invokeOk({ statusCode: 200 }), true);
    assert.equal(invokeOk({ statusCode: 401 }), false);
    assert.equal(invokeOk(null), false);
  });
});
