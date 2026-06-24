import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';
import { CompletionStatus, reportCompletion } from '@tollara/service-sdk';
import { getTollaraCredentials, resolveUsageApiUrl } from '../../lib/tollaraCredentials';
import { passthroughItemWithJson } from '../../lib/passthroughItem';
import { rewriteUsageServiceUrl } from '../../lib/usageUrls';
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
    const credentialsParsed = getTollaraCredentials(credentials);
    const { serviceSecret } = credentialsParsed;
    const items = this.getInputData();
    const returnData: INodeExecutionData[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const callbackUrlParam = this.getNodeParameter('callbackUrl', i) as string;
      const requestId = this.getNodeParameter('requestId', i) as string;
      const status = this.getNodeParameter('status', i) as string;
      const result = this.getNodeParameter('result', i, '') as string;
      const resultUrl = this.getNodeParameter('resultUrl', i, '') as string;
      const contentType = this.getNodeParameter('contentType', i, '') as string;
      const units = this.getNodeParameter('units', i, 0) as number;

      if (!callbackUrlParam?.trim()) {
        throw new Error(
          'Callback URL is empty. Reference an upstream node that holds callbackUrl (e.g. $("Build Report").item.json.callbackUrl).',
        );
      }

      const statusEnum = status === 'FAILED' ? CompletionStatus.Failed : CompletionStatus.Completed;
      const callbackUrl = rewriteUsageServiceUrl(callbackUrlParam, resolveUsageApiUrl(credentialsParsed));

      const httpResult = await reportCompletion({
        callbackUrl,
        requestId,
        status: statusEnum,
        result: result || undefined,
        resultUrl: resultUrl || undefined,
        contentType: contentType || undefined,
        units,
        serviceSecret,
      });

      returnData.push(passthroughItemWithJson(item, {
        completeSuccess: httpResult.success,
        completeHttpStatus: httpResult.httpStatus,
        completeHttpStatusText: httpResult.httpStatusText,
        completeRequestUrl: httpResult.requestUrl,
        completeResponseBody: httpResult.responseBody,
        completeNetworkError: httpResult.networkError,
        callbackUrlUsed: callbackUrl !== callbackUrlParam ? callbackUrl : undefined,
      }, i));
    }

    return [returnData];
  }
}