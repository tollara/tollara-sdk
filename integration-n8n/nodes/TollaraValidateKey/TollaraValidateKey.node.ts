import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { validateServiceKeyWithOutcome } from '../../lib/tollaraSdk';
import { optionalServiceId, requireCoreApiUrlWhenEndpointsEnabled, requireServiceSecret, resolveCoreApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { optionalServiceIdNotice, serviceIdNodeProperty, serviceSecretNodeProperty, tollaraCoreEndpointProperties } from '../../lib/nodeProperties';
import { bearerTokenFromWebhookItem } from '../../lib/webhookPayload';
import {
  accessDeniedItem,
  authFailureItem,
  isValidateKeyErrorOutcome,
  missingKeyFailureFields,
  outcomeFieldsFromValidation,
  validateKeySuccessItem,
} from '../../lib/tollaraOutcome';
import { validationResultToUserContext } from '../../lib/passthroughItem';

export class TollaraValidateKey implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Validate Key',
    name: 'tollaraValidateKey',
    icon: 'file:tollara.svg',
    usableAsTool: true,
    group: ['transform'],
    version: 4,
    description:
      'Validate a service key (typical after Webhook). Allowed = key valid and subscription grants access; Denied = caller auth failure; Error = runtime Core/network failure (respond 503). Throws on static misconfig (missing service secret, or Set API Endpoints without Core API URL).',
    defaults: { name: 'Tollara Validate Key' },
    inputs: ['main'],
    outputs: ['main', 'main', 'main'],
    outputNames: ['Allowed', 'Denied', 'Error'],
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
    requireCoreApiUrlWhenEndpointsEnabled(this);
    const serviceId = optionalServiceId(this.getNodeParameter('serviceId', 0) as string);
    const coreApiUrl = resolveCoreApiUrl(credentialsParsed);
    const serviceKeySource = this.getNodeParameter('serviceKeySource', 0) as string;
    const manualServiceKey = this.getNodeParameter('serviceKey', 0, '') as string;

    const items = this.getInputData();
    const allowedData: INodeExecutionData[] = [];
    const deniedData: INodeExecutionData[] = [];
    const errorData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const serviceKey =
        serviceKeySource === 'manual'
          ? manualServiceKey
          : bearerTokenFromWebhookItem(item);

      if (!serviceKey?.trim()) {
        deniedData.push(
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

      if (!outcome.ok) {
        const fields = outcomeFieldsFromValidation(outcome);
        if (isValidateKeyErrorOutcome(outcome.code, outcome.message)) {
          errorData.push(authFailureItem(item, fields, i));
        } else {
          deniedData.push(authFailureItem(item, fields, i));
        }
        continue;
      }

      if (!outcome.result.grantAccess) {
        deniedData.push(
          accessDeniedItem(item, validationResultToUserContext(outcome.result), i),
        );
        continue;
      }

      allowedData.push(validateKeySuccessItem(item, outcome, i));
    }

    return [allowedData, deniedData, errorData];
  }
}
