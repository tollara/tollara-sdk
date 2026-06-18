import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { invokeService, type GatewayHttpMethod } from '@tollara/service-sdk';
import { getTollaraCredentials } from '../../lib/tollaraCredentials';
import { parseJsonBody } from '../../lib/parseJsonBody';

export class TollaraInvoke implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Invoke',
    name: 'tollaraInvoke',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Invoke an agent on the gateway',
    defaults: { name: 'Tollara Invoke' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      {
        displayName: 'HTTP Method',
        name: 'httpMethod',
        type: 'options',
        options: [
          { name: 'GET', value: 'GET' },
          { name: 'POST', value: 'POST' },
          { name: 'PUT', value: 'PUT' },
          { name: 'DELETE', value: 'DELETE' },
        ],
        default: 'POST',
      },
      {
        displayName: 'Async',
        name: 'async',
        type: 'boolean',
        default: false,
        description: 'Whether to invoke asynchronously (returns requestId, progressUrl, callbackUrl)',
      },
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Service ID', name: 'serviceId', type: 'string', default: '', required: true },
      { displayName: 'Endpoint ID', name: 'endpointId', type: 'string', default: '', required: true },
      { displayName: 'Body', name: 'body', type: 'string', default: '', placeholder: '{}' },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const { apiUrl } = getTollaraCredentials(credentials);
    const method = this.getNodeParameter('httpMethod', 0) as GatewayHttpMethod;
    const isAsync = this.getNodeParameter('async', 0) as boolean;
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const serviceId = this.getNodeParameter('serviceId', 0) as string;
    const endpointId = this.getNodeParameter('endpointId', 0) as string;
    const bodyStr = this.getNodeParameter('body', 0, '') as string;

    const result = await invokeService({
      baseUrl: apiUrl,
      method,
      serviceId,
      endpointId,
      serviceKey,
      body: bodyStr || undefined,
      async: isAsync,
    });

    if (!result) {
      throw new Error('Invoke request failed');
    }

    const data = parseJsonBody(result.body);
    const output: IDataObject = {
      statusCode: result.statusCode,
      body: result.body,
      data,
      requestId: result.asyncEnvelope?.requestId ?? '',
      callbackUrl: result.asyncEnvelope?.callbackUrl ?? '',
      progressUrl: result.asyncEnvelope?.progressUrl ?? '',
    };

    return [[{ json: output }]];
  }
}
