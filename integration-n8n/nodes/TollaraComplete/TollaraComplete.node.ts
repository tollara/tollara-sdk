import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { CompletionStatus, reportCompletionFull } from '@tollara/service-sdk';
import { getTollaraCredentials } from '../../lib/tollaraCredentials';

export class TollaraComplete implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Complete',
    name: 'tollaraComplete',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Send completion to the usage service (async flows)',
    defaults: { name: 'Tollara Complete' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
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
    const credentials = await this.getCredentials('tollaraApi');
    const { serviceSecret } = getTollaraCredentials(credentials);
    const callbackUrl = this.getNodeParameter('callbackUrl', 0) as string;
    const requestId = this.getNodeParameter('requestId', 0) as string;
    const status = this.getNodeParameter('status', 0) as string;
    const result = this.getNodeParameter('result', 0, '') as string;
    const resultUrl = this.getNodeParameter('resultUrl', 0, '') as string;
    const contentType = this.getNodeParameter('contentType', 0, '') as string;
    const units = this.getNodeParameter('units', 0, 0) as number;

    const statusEnum = status === 'FAILED' ? CompletionStatus.Failed : CompletionStatus.Completed;
    const ok = await reportCompletionFull({
      callbackUrl,
      requestId,
      status: statusEnum,
      result: result || undefined,
      resultUrl: resultUrl || undefined,
      contentType: contentType || undefined,
      units,
      serviceSecret,
    });

    return [[{ json: { success: ok } }]];
  }
}
