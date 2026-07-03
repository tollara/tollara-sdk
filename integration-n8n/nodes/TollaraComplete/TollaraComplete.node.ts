import { NodeOperationError, type IExecuteFunctions, type INodeExecutionData, type INodeType, type INodeTypeDescription } from 'n8n-workflow';

import { CompletionStatus, reportCompletion } from '../../lib/tollaraSdk';

import { requireUsageApiUrlWhenEndpointsEnabled, resolveServiceSecret, resolveUsageApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';

import { serviceSecretNodeProperty, tollaraUsageEndpointProperties } from '../../lib/nodeProperties';

import { TOLLARA_DOCUMENTATION_URL, tollaraOptionalCredential } from '../../lib/tollaraConstants';

import { passthroughItemWithJson } from '../../lib/passthroughItem';

import { rewriteUsageServiceUrl } from '../../lib/usageUrls';



export class TollaraComplete implements INodeType {

  description: INodeTypeDescription = {

    displayName: 'Tollara Complete',

    name: 'tollaraComplete',

    icon: 'file:tollara.svg',
    usableAsTool: true,

    group: ['transform'],

    version: 1,

    description: 'Send completion for an async Tollara invoke',

    documentationUrl: TOLLARA_DOCUMENTATION_URL,

    defaults: { name: 'Tollara Complete' },

    credentials: [tollaraOptionalCredential],

    inputs: ['main'],

    outputs: ['main'],

    properties: [

      serviceSecretNodeProperty,

      {

        displayName: 'Callback URL',

        name: 'callbackUrl',

        type: 'string',

        default: '',

        description: 'Full callbackUrl from async invoke response',

      },

      { displayName: 'Request ID', name: 'requestId', type: 'string', default: '' },

      { displayName: 'Status', name: 'status', type: 'string', default: 'COMPLETED', options: [{ name: 'COMPLETED', value: 'COMPLETED' }, { name: 'FAILED', value: 'FAILED' }] },

      { displayName: 'Result', name: 'result', type: 'string', default: '' },

      { displayName: 'Result URL', name: 'resultUrl', type: 'string', default: '' },

      { displayName: 'Content Type', name: 'contentType', type: 'string', default: '' },

      { displayName: 'Units', name: 'units', type: 'number', default: 0 },

      ...tollaraUsageEndpointProperties,

    ],

  };



  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {

    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);

    const serviceSecret = await resolveServiceSecret(this, this.getNodeParameter('serviceSecret', 0) as string);
    requireUsageApiUrlWhenEndpointsEnabled(this);

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

        throw new NodeOperationError(
          this.getNode(),
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


