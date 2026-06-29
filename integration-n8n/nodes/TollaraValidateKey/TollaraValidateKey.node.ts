import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { validateServiceKeyWithOutcome } from '@tollara/service-sdk';
import { optionalServiceId, requireServiceSecret, resolveCoreApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { optionalServiceIdNotice, serviceIdNodeProperty, serviceSecretNodeProperty, tollaraCoreEndpointProperties } from '../../lib/nodeProperties';
import { bearerTokenFromWebhookItem } from '../../lib/webhookPayload';
import {
  authFailureItem,
  missingKeyFailureFields,
  outcomeFieldsFromValidation,
  validateKeySuccessItem,
} from '../../lib/tollaraOutcome';

export class TollaraValidateKey implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Validate Key',
    name: 'tollaraValidateKey',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 2,
    description:
      'Validate a service key (typical after Webhook). Success output adds userContext; Failure output adds tollaraErrorCode.',
    defaults: { name: 'Tollara Validate Key' },
    inputs: ['main'],
    outputs: ['main', 'main'],
    outputNames: ['Success', 'Failure'],
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
      optionalServiceIdNotice,
      serviceIdNodeProperty,
      ...tollaraCoreEndpointProperties,
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);
    const serviceSecret = requireServiceSecret(this.getNodeParameter('serviceSecret', 0) as string);
    const serviceId = optionalServiceId(this.getNodeParameter('serviceId', 0) as string);
    const coreApiUrl = resolveCoreApiUrl(credentialsParsed);
    const serviceKeySource = this.getNodeParameter('serviceKeySource', 0) as string;
    const manualServiceKey = this.getNodeParameter('serviceKey', 0, '') as string;

    const items = this.getInputData();
    const successData: INodeExecutionData[] = [];
    const failureData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const serviceKey =
        serviceKeySource === 'manual'
          ? manualServiceKey
          : bearerTokenFromWebhookItem(item);

      if (!serviceKey?.trim()) {
        failureData.push(
          authFailureItem(
            item,
            missingKeyFailureFields(
              serviceKeySource === 'manual'
                ? 'Service key is required when using Manual source'
                : 'No Bearer token found in headers.authorization',
            ),
            i,
          ),
        );
        continue;
      }

      const outcome = await validateServiceKeyWithOutcome({
        baseUrl: coreApiUrl,
        serviceKey,
        serviceId,
        serviceSecret,
      });

      if (outcome.ok) {
        successData.push(validateKeySuccessItem(item, outcome, i));
      } else {
        failureData.push(authFailureItem(item, outcomeFieldsFromValidation(outcome), i));
      }
    }

    return [successData, failureData];
  }
}
