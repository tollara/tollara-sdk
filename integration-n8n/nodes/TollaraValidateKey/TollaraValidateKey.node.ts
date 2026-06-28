import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { validateServiceKey } from '@tollara/service-sdk';
import { requireServiceId, requireServiceSecret, resolveCoreApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { serviceIdNodeProperty, serviceSecretNodeProperty, tollaraCoreEndpointProperties } from '../../lib/nodeProperties';
import { bearerTokenFromWebhookItem } from '../../lib/webhookPayload';
import { passthroughItemWithJson, validationResultToUserContext } from '../../lib/passthroughItem';

export class TollaraValidateKey implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Validate Key',
    name: 'tollaraValidateKey',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Validate a service key (typical after Webhook). Passes through all incoming data and adds userContext.',
    defaults: { name: 'Tollara Validate Key' },
    inputs: ['main'],
    outputs: ['main'],
    properties: [
      serviceSecretNodeProperty,
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
      serviceIdNodeProperty,
      ...tollaraCoreEndpointProperties,
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);
    const serviceSecret = requireServiceSecret(this.getNodeParameter('serviceSecret', 0) as string);
    const serviceId = requireServiceId(this.getNodeParameter('serviceId', 0) as string);
    const coreApiUrl = resolveCoreApiUrl(credentialsParsed);
    const serviceKeySource = this.getNodeParameter('serviceKeySource', 0) as string;
    const manualServiceKey = this.getNodeParameter('serviceKey', 0, '') as string;

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
        baseUrl: coreApiUrl,
        serviceKey,
        serviceId,
        serviceSecret,
      });

      if (!result) {
        throw new Error('Service key validation failed');
      }

      returnData.push(
        passthroughItemWithJson(item, { userContext: validationResultToUserContext(result) }, i),
      );
    }

    return [returnData];
  }
}
