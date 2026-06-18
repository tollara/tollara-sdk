import type {
  IHookFunctions,
  INodeType,
  INodeTypeDescription,
  IWebhookFunctions,
  IWebhookResponseData,
  IDataObject,
} from 'n8n-workflow';
import { verifySignatureFromHeaders, getUserContext } from '@tollara/service-sdk';
import { getTollaraCredentials } from '../../lib/tollaraCredentials';

function requestPayload(req: ReturnType<IWebhookFunctions['getRequestObject']>): string {
  const rawBody = (req as { rawBody?: Buffer | string }).rawBody;
  if (rawBody != null) {
    return typeof rawBody === 'string' ? rawBody : rawBody.toString('utf8');
  }
  const body = req.body;
  if (body == null) return '';
  return typeof body === 'object' ? JSON.stringify(body) : String(body);
}

export class TollaraTrigger implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Trigger',
    name: 'tollaraTrigger',
    icon: 'file:tollara.png',
    group: ['trigger'],
    version: 1,
    description: 'Webhook that verifies HMAC and parses X-Tollara-* headers',
    defaults: { name: 'Tollara Trigger' },
    inputs: [],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    webhooks: [
      {
        name: 'default',
        httpMethod: 'POST',
        responseMode: 'onReceived',
        path: 'tollara',
      },
    ],
    properties: [],
  };

  webhookMethods = {
    default: {
      async checkExists(this: IHookFunctions): Promise<boolean> {
        return false;
      },
      async create(this: IHookFunctions): Promise<boolean> {
        return true;
      },
      async delete(this: IHookFunctions): Promise<boolean> {
        return true;
      },
    },
  };

  async webhook(this: IWebhookFunctions): Promise<IWebhookResponseData> {
    const credentials = await this.getCredentials('tollaraApi');
    const { serviceSecret } = getTollaraCredentials(credentials);
    const req = this.getRequestObject();
    const headers = req.headers as Record<string, string>;
    const payload = requestPayload(req);
    const valid = verifySignatureFromHeaders(serviceSecret, headers, payload);

    if (!valid) {
      throw new Error('Invalid HMAC signature');
    }

    const userContext = getUserContext(headers);
    const body = req.body;

    return {
      workflowData: [[{ json: { body, userContext } as IDataObject }]],
    };
  }
}
