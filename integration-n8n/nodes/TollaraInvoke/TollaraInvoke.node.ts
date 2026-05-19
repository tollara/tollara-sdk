import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';

export class TollaraInvoke implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Invoke',
    name: 'tollaraInvoke',
    icon: 'file:tollara.svg',
    group: ['transform'],
    version: 1,
    description: 'Invoke an agent on the gateway',
    defaults: { name: 'Tollara Invoke' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Service ID', name: 'serviceId', type: 'string', default: '', required: true },
      { displayName: 'Endpoint ID', name: 'endpointId', type: 'string', default: '', required: true },
      { displayName: 'Body', name: 'body', type: 'string', default: '', placeholder: '{}' },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const gatewayUrl = ((credentials as { gatewayUrl?: string }).gatewayUrl || '').replace(/\/$/, '');
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const serviceId = this.getNodeParameter('serviceId', 0) as string;
    const endpointId = this.getNodeParameter('endpointId', 0) as string;
    const bodyStr = this.getNodeParameter('body', 0, '{}') as string;

    const url = `${gatewayUrl}/api/service/${serviceId}/endpoint/${endpointId}/invoke`;
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${serviceKey}`,
      },
      body: bodyStr || '{}',
    });

    const data = await res.json().catch(() => ({}));
    return [[{ json: { status: res.status, data } }]];
  }
}
