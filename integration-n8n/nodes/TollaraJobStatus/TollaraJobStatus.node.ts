import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { getRequestStatus } from '../../lib/tollaraSdk';
import { resolveGatewayApiUrl, requireGatewayApiUrlWhenEndpointsEnabled, resolveServiceKey, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { tollaraGatewayEndpointProperties } from '../../lib/nodeProperties';
import { TOLLARA_DOCUMENTATION_URL, tollaraOptionalCredential } from '../../lib/tollaraConstants';
import { parseJsonBody } from '../../lib/parseJsonBody';

export class TollaraJobStatus implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Job Status',
    name: 'tollaraJobStatus',
    icon: 'file:tollara.svg',
    usableAsTool: true,
    group: ['transform'],
    version: 1,
    description: 'Poll async job status for a Tollara invoke request',
    documentationUrl: TOLLARA_DOCUMENTATION_URL,
    defaults: { name: 'Tollara Job Status' },
    credentials: [tollaraOptionalCredential],
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
    requireGatewayApiUrlWhenEndpointsEnabled(this);
    const gatewayApiUrl = resolveGatewayApiUrl(credentialsParsed);
    const serviceKey = await resolveServiceKey(this, this.getNodeParameter('serviceKey', 0) as string);
    const requestId = this.getNodeParameter('requestId', 0) as string;

    const result = await getRequestStatus({ baseUrl: gatewayApiUrl, requestId, serviceKey });

    const output: IDataObject = {
      tollaraOk: result.ok,
      statusCode: result.status,
      ok: result.ok,
      body: result.body,
      data: parseJsonBody(result.body),
    };

    return [[{ json: output }]];
  }
}
