import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { getRequestStatus } from '@tollara/service-sdk';
import { resolveGatewayApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { tollaraGatewayEndpointProperties } from '../../lib/nodeProperties';
import { parseJsonBody } from '../../lib/parseJsonBody';

export class TollaraJobStatus implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Job Status',
    name: 'tollaraJobStatus',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Poll async job status from the gateway',
    defaults: { name: 'Tollara Job Status' },
    inputs: ['main'],
    outputs: ['main'],
    properties: [
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Request ID', name: 'requestId', type: 'string', default: '', required: true },
      ...tollaraGatewayEndpointProperties,
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);
    const gatewayApiUrl = resolveGatewayApiUrl(credentialsParsed);
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const requestId = this.getNodeParameter('requestId', 0) as string;

    const result = await getRequestStatus({ baseUrl: gatewayApiUrl, requestId, serviceKey });

    const output: IDataObject = {
      statusCode: result.status,
      ok: result.ok,
      body: result.body,
      data: parseJsonBody(result.body),
    };

    return [[{ json: output }]];
  }
}
