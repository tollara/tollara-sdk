import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { reportProgress } from '@tollara/service-sdk';

export class TollaraProgress implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Progress',
    name: 'tollaraProgress',
    icon: 'file:tollara.svg',
    group: ['transform'],
    version: 1,
    description: 'Send progress update to the usage service (async flows)',
    defaults: { name: 'Tollara Progress' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      { displayName: 'Progress URL', name: 'progressUrl', type: 'string', default: '', required: true, description: 'Full progressUrl from async invoke response' },
      { displayName: 'Request ID', name: 'requestId', type: 'string', default: '' },
      { displayName: 'Stage', name: 'stage', type: 'string', default: '' },
      { displayName: 'Percentage Complete', name: 'percentageComplete', type: 'number', default: 0 },
      { displayName: 'Error Message', name: 'errorMessage', type: 'string', default: '' },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const serviceSecret = (credentials as { serviceSecret?: string }).serviceSecret as string;
    const progressUrl = this.getNodeParameter('progressUrl', 0) as string;
    const requestId = this.getNodeParameter('requestId', 0) as string;
    const stage = this.getNodeParameter('stage', 0) as string;
    const percentageComplete = this.getNodeParameter('percentageComplete', 0) as number;
    const errorMessage = this.getNodeParameter('errorMessage', 0, '') as string;

    const ok = await reportProgress({
      progressUrl,
      requestId,
      stage,
      percentageComplete,
      errorMessage: errorMessage || undefined,
      serviceSecret,
    });

    return [[{ json: { success: ok } }]];
  }
}
