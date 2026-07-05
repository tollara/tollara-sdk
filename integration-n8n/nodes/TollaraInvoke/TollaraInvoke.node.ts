import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { invokeService, type GatewayHttpMethod } from '../../lib/tollaraSdk';
import { resolveGatewayApiUrl, requireGatewayApiUrlWhenEndpointsEnabled, resolveServiceKey, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { tollaraGatewayEndpointProperties } from '../../lib/nodeProperties';
import { TOLLARA_DOCUMENTATION_URL } from '../../lib/tollaraConstants';
import { parseJsonBody } from '../../lib/parseJsonBody';
import { invokeOk } from '../../lib/tollaraOutcome';

export class TollaraInvoke implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Invoke',
    name: 'tollaraInvoke',
    icon: 'file:tollara-brand.svg',
    usableAsTool: true,
    group: ['transform'],
    version: 1,
    description: 'Invoke a listed Tollara service (sync or async)',
    documentationUrl: TOLLARA_DOCUMENTATION_URL,
    defaults: { name: 'Tollara Invoke' },
    credentials: [
      {
        name: 'tollaraApi',
        required: false,
        testedBy: 'tollaraValidateKey',
      },
    ],
    inputs: ['main'],
    outputs: ['main'],
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
      ...tollaraGatewayEndpointProperties,
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);
    requireGatewayApiUrlWhenEndpointsEnabled(this);
    const gatewayApiUrl = resolveGatewayApiUrl(credentialsParsed);
    const method = this.getNodeParameter('httpMethod', 0) as GatewayHttpMethod;
    const isAsync = this.getNodeParameter('async', 0) as boolean;
    const serviceKey = await resolveServiceKey(this, this.getNodeParameter('serviceKey', 0) as string);
    const serviceId = this.getNodeParameter('serviceId', 0) as string;
    const endpointId = this.getNodeParameter('endpointId', 0) as string;
    const bodyStr = this.getNodeParameter('body', 0, '') as string;

    const result = await invokeService({
      baseUrl: gatewayApiUrl,
      method,
      serviceId,
      endpointId,
      serviceKey,
      body: bodyStr || undefined,
      async: isAsync,
    });

    const data = result ? parseJsonBody(result.body) : null;
    const output: IDataObject = {
      tollaraOk: invokeOk(result),
      tollaraErrorCode: result ? undefined : 'NETWORK',
      tollaraErrorMessage: result ? undefined : 'Invoke request failed',
      statusCode: result?.statusCode ?? 0,
      body: result?.body ?? '',
      data: data ?? {},
      requestId: result?.asyncEnvelope?.requestId ?? '',
      callbackUrl: result?.asyncEnvelope?.callbackUrl ?? '',
      progressUrl: result?.asyncEnvelope?.progressUrl ?? '',
    };

    return [[{ json: output }]];
  }
}
