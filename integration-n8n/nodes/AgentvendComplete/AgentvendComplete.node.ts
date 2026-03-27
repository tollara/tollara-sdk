import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { reportCompletion } from '@agentvend/agent-sdk';

export class AgentvendComplete implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'AgentVend Complete',
    name: 'agentvendComplete',
    icon: 'file:agentvend.svg',
    group: ['transform'],
    version: 1,
    description: 'Send completion to the usage service (async flows)',
    defaults: { name: 'AgentVend Complete' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'agentvendApi', required: true }],
    properties: [
      { displayName: 'Callback URL', name: 'callbackUrl', type: 'string', default: '', required: true, description: 'Full callbackUrl from async invoke response' },
      { displayName: 'Request ID', name: 'requestId', type: 'string', default: '' },
      { displayName: 'Status', name: 'status', type: 'string', default: 'COMPLETED', options: [{ name: 'COMPLETED', value: 'COMPLETED' }, { name: 'FAILED', value: 'FAILED' }] },
      { displayName: 'Result', name: 'result', type: 'string', default: '' },
      { displayName: 'Result URL', name: 'resultUrl', type: 'string', default: '' },
      { displayName: 'Content Type', name: 'contentType', type: 'string', default: '' },
      { displayName: 'Units', name: 'units', type: 'number', default: 0 },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('agentvendApi');
    const agentSecret = (credentials as { agentSecret?: string }).agentSecret as string;
    const callbackUrl = this.getNodeParameter('callbackUrl', 0) as string;
    const requestId = this.getNodeParameter('requestId', 0) as string;
    const status = this.getNodeParameter('status', 0) as string;
    const result = this.getNodeParameter('result', 0, '') as string;
    const resultUrl = this.getNodeParameter('resultUrl', 0, '') as string;
    const contentType = this.getNodeParameter('contentType', 0, '') as string;
    const units = this.getNodeParameter('units', 0, 0) as number;

    const ok = await reportCompletion({
      callbackUrl,
      requestId,
      status,
      result: result || undefined,
      resultUrl: resultUrl || undefined,
      contentType: contentType || undefined,
      units,
      agentSecret,
    });

    return [[{ json: { success: ok } }]];
  }
}
