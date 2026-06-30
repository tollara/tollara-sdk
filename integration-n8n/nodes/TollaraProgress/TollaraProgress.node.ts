import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription } from 'n8n-workflow';

import { reportProgress } from '@tollara/service-sdk';

import { requireServiceSecret, requireUsageApiUrlWhenEndpointsEnabled, resolveUsageApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';

import { serviceSecretNodeProperty, tollaraUsageEndpointProperties } from '../../lib/nodeProperties';

import { passthroughItemWithJson } from '../../lib/passthroughItem';

import { rewriteUsageServiceUrl } from '../../lib/usageUrls';



export class TollaraProgress implements INodeType {

  description: INodeTypeDescription = {

    displayName: 'Tollara Progress',

    name: 'tollaraProgress',

    icon: 'file:tollara.png',

    group: ['transform'],

    version: 1,

    description: 'Send progress update to the usage service (async flows)',

    defaults: { name: 'Tollara Progress' },

    inputs: ['main'],

    outputs: ['main'],

    properties: [

      serviceSecretNodeProperty,

      {

        displayName: 'Progress URL',

        name: 'progressUrl',

        type: 'string',

        default: '',

        description: 'Full progressUrl from async invoke response',

      },

      { displayName: 'Request ID', name: 'requestId', type: 'string', default: '' },

      { displayName: 'Stage', name: 'stage', type: 'string', default: '' },

      { displayName: 'Percentage Complete', name: 'percentageComplete', type: 'number', default: 0 },

      { displayName: 'Error Message', name: 'errorMessage', type: 'string', default: '' },

      ...tollaraUsageEndpointProperties,

    ],

  };



  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {

    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);

    const serviceSecret = requireServiceSecret(this.getNodeParameter('serviceSecret', 0) as string);
    requireUsageApiUrlWhenEndpointsEnabled(this);

    const items = this.getInputData();

    const returnData: INodeExecutionData[] = [];



    for (let i = 0; i < items.length; i++) {

      const item = items[i];

      const progressUrlParam = this.getNodeParameter('progressUrl', i) as string;

      const requestId = this.getNodeParameter('requestId', i) as string;

      const stage = this.getNodeParameter('stage', i) as string;

      const percentageComplete = this.getNodeParameter('percentageComplete', i) as number;

      const errorMessage = this.getNodeParameter('errorMessage', i, '') as string;



      if (!progressUrlParam?.trim()) {

        throw new Error(

          'Progress URL is empty. Reference an upstream node that holds progressUrl (e.g. $("Parse Async Envelope").item.json.progressUrl).',

        );

      }



      const progressUrl = rewriteUsageServiceUrl(progressUrlParam, resolveUsageApiUrl(credentialsParsed));



      const result = await reportProgress({

        progressUrl,

        requestId,

        stage,

        percentageComplete,

        errorMessage: errorMessage || undefined,

        serviceSecret,

      });



      returnData.push(passthroughItemWithJson(item, {

        progressSuccess: result.success,

        progressHttpStatus: result.httpStatus,

        progressHttpStatusText: result.httpStatusText,

        progressRequestUrl: result.requestUrl,

        progressResponseBody: result.responseBody,

        progressNetworkError: result.networkError,

        progressUrlUsed: progressUrl !== progressUrlParam ? progressUrl : undefined,

      }, i));

    }



    return [returnData];

  }

}


