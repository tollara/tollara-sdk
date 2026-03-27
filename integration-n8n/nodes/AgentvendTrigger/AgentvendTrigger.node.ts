import type { ITriggerFunctions, IHookFunctions, INodeType, INodeTypeDescription, IWebhookResponseData, IDataObject } from 'n8n-workflow';
import { verifySignature, getUserContext } from '@agentvend/agent-sdk';

export class AgentvendTrigger implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'AgentVend Trigger',
    name: 'agentvendTrigger',
    icon: 'file:agentvend.svg',
    group: ['trigger'],
    version: 1,
    description: 'Webhook that verifies HMAC and parses X-AgentVend-* headers',
    defaults: { name: 'AgentVend Trigger' },
    inputs: [],
    outputs: ['main'],
    credentials: [{ name: 'agentvendApi', required: true }],
    webhooks: [
      {
        name: 'default',
        httpMethod: 'POST',
        responseMode: 'onReceived',
        path: 'agentvend',
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
    const credentials = await this.getCredentials('agentvendApi');
    const agentSecret = (credentials as { agentSecret?: string }).agentSecret as string;
    const req = (this as unknown as { getRequestObject?: () => { body: unknown; headers: Record<string, string> } }).getRequestObject?.();
    if (!req) return {};
    const body = req.body;
    const headers = req.headers || {};
    const signature = headers['x-agentvend-signature'] as string;
    const timestamp = headers['x-agentvend-timestamp'] as string;
    const userId = headers['x-agentvend-user-id'] as string;
    const plan = headers['x-agentvend-plan'] as string;
    const rolesHeader = headers['x-agentvend-roles'] as string;
    const quotaRemaining = headers['x-agentvend-quota-remaining'] as string;
    const subscriptionActive = headers['x-agentvend-subscription-active'] as string;

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
      'X-AgentVend-User-ID': userId,
      'X-AgentVend-Plan': plan,
      'X-AgentVend-Roles': rolesHeader,
      'X-AgentVend-Quota-Remaining': quotaRemaining,
      'X-AgentVend-Subscription-Active': subscriptionActive,
    });

    this.emit([this.helpers.returnJsonArray([{ body, userContext } as IDataObject])]);
    return {};
  }
}
