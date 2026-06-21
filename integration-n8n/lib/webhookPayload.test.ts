import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { INodeExecutionData } from 'n8n-workflow';
import { gatewayAsyncSigningPayload, signedPayloadFromWebhookItem } from './webhookPayload';

describe('gatewayAsyncSigningPayload', () => {
  it('strips progress_url and callback_url from async gateway body', () => {
    const body = {
      payload: '{"topic":"Docker"}',
      request_id: 'req-123',
      progress_url: 'http://usage/progress/req-123?signature=abc',
      callback_url: 'http://usage/complete/req-123?signature=def',
    };
    assert.equal(
      gatewayAsyncSigningPayload(body),
      '{"payload":"{\\"topic\\":\\"Docker\\"}","request_id":"req-123"}',
    );
  });

  it('serializes non-string payload field like Jackson ObjectMapper', () => {
    const body = {
      payload: { topic: 'Docker' },
      request_id: 'req-456',
    };
    assert.equal(
      gatewayAsyncSigningPayload(body),
      '{"payload":"{\\"topic\\":\\"Docker\\"}","request_id":"req-456"}',
    );
  });

  it('returns null when request_id is absent (sync body)', () => {
    assert.equal(gatewayAsyncSigningPayload({ topic: 'Docker' }), null);
  });
});

describe('signedPayloadFromWebhookItem', () => {
  it('uses pre-URL async signing payload from raw binary body', () => {
    const forwardedBody = JSON.stringify({
      payload: '{"topic":"Docker"}',
      request_id: 'req-789',
      progress_url: 'http://usage/progress/req-789',
      callback_url: 'http://usage/complete/req-789',
    });
    const item: INodeExecutionData = {
      json: { headers: {} },
      binary: {
        data: {
          data: Buffer.from(forwardedBody, 'utf8').toString('base64'),
          mimeType: 'application/json',
        },
      },
    };

    assert.equal(
      signedPayloadFromWebhookItem(item, 'data'),
      '{"payload":"{\\"topic\\":\\"Docker\\"}","request_id":"req-789"}',
    );
  });

  it('passes through sync JSON body unchanged', () => {
    const item: INodeExecutionData = {
      json: {
        headers: { 'content-length': '18' },
        body: { topic: 'Docker' },
      },
    };

    assert.equal(signedPayloadFromWebhookItem(item, 'data'), '{"topic":"Docker"}');
  });

  it('treats content-length 0 as empty signed payload', () => {
    const item: INodeExecutionData = {
      json: {
        headers: { 'content-length': '0' },
        body: {},
      },
    };

    assert.equal(signedPayloadFromWebhookItem(item, 'data'), '');
  });
});
