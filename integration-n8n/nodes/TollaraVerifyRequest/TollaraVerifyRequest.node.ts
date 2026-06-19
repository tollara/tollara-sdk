import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { verifySignatureFromHeaders, getUserContext } from '@tollara/service-sdk';
import { getTollaraCredentials } from '../../lib/tollaraCredentials';
import { headersFromWebhookItem, signedPayloadFromWebhookItem } from '../../lib/webhookPayload';
import { passthroughItemWithJson } from '../../lib/passthroughItem';

export class TollaraVerifyRequest implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Verify Request',
    name: 'tollaraVerifyRequest',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description:
      'Verify Tollara HMAC on output from the n8n Webhook node. Passes through all webhook data and adds userContext when valid.',
    defaults: { name: 'Tollara Verify Request' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
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
    const credentials = await this.getCredentials('tollaraApi');
    const { serviceSecret } = getTollaraCredentials(credentials);
    const rawBodyBinaryProperty = this.getNodeParameter('rawBodyBinaryProperty', 0, 'data') as string;

    const items = this.getInputData();
    const returnData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const headers = headersFromWebhookItem(item);
      const payload = signedPayloadFromWebhookItem(item, rawBodyBinaryProperty);
      const valid = verifySignatureFromHeaders(serviceSecret, headers, payload);

      if (!valid) {
        throw new Error('Invalid HMAC signature');
      }

      const userContext = getUserContext(headers);

      returnData.push(passthroughItemWithJson(item, { userContext }, i));
    }

    return [returnData];
  }
}
