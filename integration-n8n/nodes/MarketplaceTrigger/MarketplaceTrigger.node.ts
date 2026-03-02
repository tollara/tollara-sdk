import type { ITriggerFunctions, IHookFunctions, INodeType, INodeTypeDescription, IWebhookResponseData, IDataObject } from 'n8n-workflow';
import { verifySignature, getUserContext } from '@marketplace/agent-sdk';

export class MarketplaceTrigger implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Marketplace Trigger',
    name: 'marketplaceTrigger',
    icon: 'file:marketplace.svg',
    group: ['trigger'],
    version: 1,
    description: 'Webhook that verifies HMAC and parses X-Marketplace-* headers',
    defaults: { name: 'Marketplace Trigger' },
    inputs: [],
    outputs: ['main'],
    credentials: [{ name: 'marketplaceApi', required: true }],
    webhooks: [
      {
        name: 'default',
        httpMethod: 'POST',
        responseMode: 'onReceived',
        path: 'marketplace',
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
    const credentials = await this.getCredentials('marketplaceApi');
    const agentSecret = (credentials as { agentSecret?: string }).agentSecret as string;
    const req = (this as unknown as { getRequestObject?: () => { body: unknown; headers: Record<string, string> } }).getRequestObject?.();
    if (!req) return {};
    const body = req.body;
    const headers = req.headers || {};
    const signature = headers['x-marketplace-signature'] as string;
    const timestamp = headers['x-marketplace-timestamp'] as string;
    const userId = headers['x-marketplace-user-id'] as string;
    const plan = headers['x-marketplace-plan'] as string;
    const rolesHeader = headers['x-marketplace-roles'] as string;
    const quotaRemaining = headers['x-marketplace-quota-remaining'] as string;
    const subscriptionActive = headers['x-marketplace-subscription-active'] as string;

    const valid = verifySignature(agentSecret, {
      signature: signature || '',
      timestamp: timestamp || '',
      payload: typeof body === 'object' ? JSON.stringify(body) : (body as string) ?? '',
      userId: userId ?? null,
      plan: plan ?? null,
      roles: rolesHeader ? rolesHeader.split(',') : [],
      quotaRemaining: quotaRemaining ?? null,
    });

    if (!valid) {
      throw new Error('Invalid HMAC signature');
    }

    const userContext = getUserContext({
      'X-Marketplace-User-ID': userId,
      'X-Marketplace-Plan': plan,
      'X-Marketplace-Roles': rolesHeader,
      'X-Marketplace-Quota-Remaining': quotaRemaining,
      'X-Marketplace-Subscription-Active': subscriptionActive,
    });

    this.emit([this.helpers.returnJsonArray([{ body, userContext } as IDataObject])]);
    return {};
  }
}
