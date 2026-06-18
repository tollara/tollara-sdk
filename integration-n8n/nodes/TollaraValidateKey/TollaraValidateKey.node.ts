import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { validateServiceKey } from '@tollara/service-sdk';
import { getTollaraCredentials, resolveServiceId } from '../../lib/tollaraCredentials';
import { bearerTokenFromWebhookItem } from '../../lib/webhookPayload';

export class TollaraValidateKey implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Validate Key',
    name: 'tollaraValidateKey',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Validate a service key via the core service (typical after the n8n Webhook node)',
    defaults: { name: 'Tollara Validate Key' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      {
        displayName: 'Service Key Source',
        name: 'serviceKeySource',
        type: 'options',
        options: [
          { name: 'From Webhook Authorization Header', value: 'webhookAuthorization' },
          { name: 'Manual', value: 'manual' },
        ],
        default: 'webhookAuthorization',
        description:
          'When set to From Webhook, reads the Bearer token from the incoming item headers (use after the n8n Webhook node)',
      },
      {
        displayName: 'Service Key',
        name: 'serviceKey',
        type: 'string',
        typeOptions: { password: true },
        default: '',
        required: true,
        displayOptions: { show: { serviceKeySource: ['manual'] } },
      },
      {
        displayName: 'Service ID Override',
        name: 'serviceId',
        type: 'string',
        default: '',
        placeholder: 'Leave blank to use Service ID from credentials',
        description:
          'Optional. Overrides the Service ID in your Tollara API credentials. Find the UUID in the Tollara Service Workspace under your service settings.',
      },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const { apiUrl, serviceSecret, serviceId: credentialServiceId } = getTollaraCredentials(credentials);
    const serviceKeySource = this.getNodeParameter('serviceKeySource', 0) as string;
    const manualServiceKey = this.getNodeParameter('serviceKey', 0, '') as string;
    const nodeServiceId = (this.getNodeParameter('serviceId', 0) as string) || undefined;
    const serviceId = resolveServiceId(credentialServiceId, nodeServiceId);

    const items = this.getInputData();
    const returnData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const serviceKey =
        serviceKeySource === 'manual'
          ? manualServiceKey
          : bearerTokenFromWebhookItem(item);

      if (!serviceKey?.trim()) {
        throw new Error(
          serviceKeySource === 'manual'
            ? 'Service key is required when using Manual source'
            : 'No Bearer token found in headers.authorization — place this node after the n8n Webhook node, or switch to Manual',
        );
      }

      const result = await validateServiceKey({
        baseUrl: apiUrl,
        serviceKey,
        serviceId,
        serviceSecret,
      });

      if (!result) {
        throw new Error('Service key validation failed');
      }

      returnData.push({
        json: { ...(item.json as IDataObject), ...result } as IDataObject,
        ...(item.binary ? { binary: item.binary } : {}),
        pairedItem: { item: i },
      });
    }

    return [returnData];
  }
}
