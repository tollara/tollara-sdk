import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { getRequestResult } from '@tollara/service-sdk';
import { getTollaraCredentials, resolveGatewayApiUrl } from '../../lib/tollaraCredentials';
import { parseJsonBody } from '../../lib/parseJsonBody';

export class TollaraJobResult implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Job Result',
    name: 'tollaraJobResult',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Fetch async job result from the gateway',
    defaults: { name: 'Tollara Job Result' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Request ID', name: 'requestId', type: 'string', default: '', required: true },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const credentialsParsed = getTollaraCredentials(credentials);
    const gatewayApiUrl = resolveGatewayApiUrl(credentialsParsed);
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const requestId = this.getNodeParameter('requestId', 0) as string;

    const result = await getRequestResult({ baseUrl: gatewayApiUrl, requestId, serviceKey });

    const output: IDataObject = {
      statusCode: result.status,
      ok: result.ok,
      body: result.body,
      data: parseJsonBody(result.body),
    };

    return [[{ json: output }]];
  }
}
