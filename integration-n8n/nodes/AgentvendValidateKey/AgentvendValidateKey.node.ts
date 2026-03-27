import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { validateAgentKey } from '@agentvend/agent-sdk';

export class AgentvendValidateKey implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'AgentVend Validate Key',
    name: 'agentvendValidateKey',
    icon: 'file:agentvend.svg',
    group: ['transform'],
    version: 1,
    description: 'Validate an agent key via the core service',
    defaults: { name: 'AgentVend Validate Key' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'agentvendApi', required: true }],
    properties: [
      { displayName: 'Agent Key', name: 'agentKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Agent ID', name: 'agentId', type: 'string', default: '' },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('agentvendApi');
    const coreServiceUrl = (credentials as { coreServiceUrl?: string }).coreServiceUrl as string;
    const agentSecret = (credentials as { agentSecret?: string }).agentSecret as string;
    const agentKey = this.getNodeParameter('agentKey', 0) as string;
    const agentId = (this.getNodeParameter('agentId', 0) as string) || undefined;

    const result = await validateAgentKey({
      coreServiceUrl,
      agentKey,
      agentId: agentId ?? null,
      agentSecret,
    });

    return [[{ json: (result ?? {}) as IDataObject }]];
  }
}
