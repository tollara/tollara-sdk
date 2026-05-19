import type { ITriggerFunctions, IHookFunctions, INodeType, INodeTypeDescription, IWebhookResponseData, IDataObject } from 'n8n-workflow';
import { verifySignatureFromHeaders, getUserContext } from '@tollara/service-sdk';

export class TollaraTrigger implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Trigger',
    name: 'tollaraTrigger',
    icon: 'file:tollara.svg',
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

  // n8n trigger context may be ITriggerFunctions with emit; type def expects IWebhookFunctions
  // @ts-expect-error - trigger context provides emit for webhook response
  async webhook(this: ITriggerFunctions): Promise<IWebhookResponseData> {
    const credentials = await this.getCredentials('tollaraApi');
    const serviceSecret = (credentials as { serviceSecret?: string }).serviceSecret as string;
    const req = (this as unknown as { getRequestObject?: () => { body: unknown; headers: Record<string, string> } }).getRequestObject?.();
    if (!req) return {};
    const body = req.body;
    const headers = req.headers || {};
    const payload = typeof body === 'object' ? JSON.stringify(body) : (body as string) ?? '';
    const valid = verifySignatureFromHeaders(serviceSecret, headers as Record<string, string>, payload);

    if (!valid) {
      throw new Error('Invalid HMAC signature');
    }

    const userContext = getUserContext(headers as Record<string, string>);

    this.emit([this.helpers.returnJsonArray([{ body, userContext } as IDataObject])]);
    return {};
  }
}
