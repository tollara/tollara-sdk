import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';

export class MarketplaceInvoke implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Marketplace Invoke',
    name: 'marketplaceInvoke',
    icon: 'file:marketplace.svg',
    group: ['transform'],
    version: 1,
    description: 'Invoke an agent on the gateway',
    defaults: { name: 'Marketplace Invoke' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'marketplaceApi', required: true }],
    properties: [
      { displayName: 'Agent Key', name: 'agentKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Agent ID', name: 'agentId', type: 'string', default: '', required: true },
      { displayName: 'Endpoint ID', name: 'endpointId', type: 'string', default: '', required: true },
      { displayName: 'Body', name: 'body', type: 'string', default: '', placeholder: '{}' },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('marketplaceApi');
    const gatewayUrl = ((credentials as { gatewayUrl?: string }).gatewayUrl || '').replace(/\/$/, '');
    const agentKey = this.getNodeParameter('agentKey', 0) as string;
    const agentId = this.getNodeParameter('agentId', 0) as string;
    const endpointId = this.getNodeParameter('endpointId', 0) as string;
    const bodyStr = this.getNodeParameter('body', 0, '{}') as string;

    const url = `${gatewayUrl}/api/agent/${agentId}/endpoint/${endpointId}/invoke`;
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${agentKey}`,
      },
      body: bodyStr || '{}',
    });

    const data = await res.json().catch(() => ({}));
    return [[{ json: { status: res.status, data } }]];
  }
}
