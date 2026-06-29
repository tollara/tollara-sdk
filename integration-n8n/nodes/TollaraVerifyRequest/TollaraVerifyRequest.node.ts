import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { verifySignatureFromHeaders, getUserContext } from '@tollara/service-sdk';
import { requireServiceSecret } from '../../lib/tollaraCredentials';
import { serviceSecretNodeProperty } from '../../lib/nodeProperties';
import { headersFromWebhookItem, signedPayloadFromWebhookItem } from '../../lib/webhookPayload';
import { passthroughItemWithJson, headerUserContextToPassthrough } from '../../lib/passthroughItem';
import { authFailureItem, hmacFailureFields } from '../../lib/tollaraOutcome';

export class TollaraVerifyRequest implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Verify Request',
    name: 'tollaraVerifyRequest',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 2,
    description:
      'Verify Tollara HMAC on output from the n8n Webhook node. Success output adds userContext; Failure output for invalid HMAC.',
    defaults: { name: 'Tollara Verify Request' },
    inputs: ['main'],
    outputs: ['main', 'main'],
    outputNames: ['Success', 'Failure'],
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
    const successData: INodeExecutionData[] = [];
    const failureData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const headers = headersFromWebhookItem(item);
      const payload = signedPayloadFromWebhookItem(item, rawBodyBinaryProperty);
      const valid = verifySignatureFromHeaders(serviceSecret, headers, payload);

      if (!valid) {
        failureData.push(authFailureItem(item, hmacFailureFields(), i));
        continue;
      }

      const userContext = headerUserContextToPassthrough(getUserContext(headers));
      successData.push(
        passthroughItemWithJson(item, { tollaraOk: true, userContext }, i),
      );
    }

    return [successData, failureData];
  }
}
