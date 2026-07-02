import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { verifySignatureFromHeaders, getUserContext } from '@tollara/service-sdk';
import { requireServiceSecret } from '../../lib/tollaraCredentials';
import { serviceSecretNodeProperty } from '../../lib/nodeProperties';
import { headersFromWebhookItem, signedPayloadFromWebhookItem } from '../../lib/webhookPayload';
import { passthroughItemWithJson, headerUserContextToPassthrough } from '../../lib/passthroughItem';
import { accessDeniedItem, authFailureItem, hmacFailureFields } from '../../lib/tollaraOutcome';

export class TollaraVerifyRequest implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Verify Request',
    name: 'tollaraVerifyRequest',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 4,
    description:
      'Verify Tollara HMAC and subscription access on Webhook output. Allowed = proceed; Denied = invalid HMAC or inactive subscription. Throws on missing service secret (static misconfig).',
    defaults: { name: 'Tollara Verify Request' },
    inputs: ['main'],
    outputs: ['main', 'main'],
    outputNames: ['Allowed', 'Denied'],
    properties: [
      serviceSecretNodeProperty,
      {
        displayName: 'Raw Body Binary Property',
        name: 'rawBodyBinaryProperty',
        type: 'string',
        default: 'data',
        description:
          'Binary property name from the Webhook node when "Raw Body" is enabled. Used for HMAC verification.',
      },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const serviceSecret = requireServiceSecret(this.getNodeParameter('serviceSecret', 0) as string);
    const rawBodyBinaryProperty = this.getNodeParameter('rawBodyBinaryProperty', 0, 'data') as string;

    const items = this.getInputData();
    const allowedData: INodeExecutionData[] = [];
    const deniedData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const headers = headersFromWebhookItem(item);
      const payload = signedPayloadFromWebhookItem(item, rawBodyBinaryProperty);
      const valid = verifySignatureFromHeaders(serviceSecret, headers, payload);

      if (!valid) {
        deniedData.push(authFailureItem(item, hmacFailureFields(), i));
        continue;
      }

      const userContext = headerUserContextToPassthrough(getUserContext(headers));
      if (!userContext.grantAccess) {
        deniedData.push(accessDeniedItem(item, userContext, i));
        continue;
      }

      allowedData.push(
        passthroughItemWithJson(item, { tollaraOk: true, userContext }, i),
      );
    }

    return [allowedData, deniedData];
  }
}
