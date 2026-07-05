import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { estimateUsage } from '../../lib/tollaraSdk';
import { getTollaraApiCredential, optionalServiceId, requireCoreApiUrlWhenEndpointsEnabled, resolveCoreApiUrl, resolveServiceKey, resolveServiceSecret, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { optionalServiceIdNotice, serviceIdNodeProperty, serviceSecretNodeProperty, tollaraCoreEndpointProperties } from '../../lib/nodeProperties';
import { TOLLARA_DOCUMENTATION_URL } from '../../lib/tollaraConstants';

export class TollaraEstimateUsage implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Estimate Usage',
    name: 'tollaraEstimateUsage',
    icon: 'file:tollara-brand.svg',
    usableAsTool: true,
    group: ['transform'],
    version: 1,
    description: 'Estimate usage cost and quota for a service key',
    documentationUrl: TOLLARA_DOCUMENTATION_URL,
    defaults: { name: 'Tollara Estimate Usage' },
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
      serviceSecretNodeProperty,
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      optionalServiceIdNotice,
      serviceIdNodeProperty,
      { displayName: 'Estimated Units', name: 'estimatedUnits', type: 'number', default: 1, required: true },
      ...tollaraCoreEndpointProperties,
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);
    const apiCredential = await getTollaraApiCredential(this);
    const serviceSecret = await resolveServiceSecret(this, this.getNodeParameter('serviceSecret', 0) as string);
    requireCoreApiUrlWhenEndpointsEnabled(this);
    const coreApiUrl = resolveCoreApiUrl(credentialsParsed);
    const serviceKey = await resolveServiceKey(this, this.getNodeParameter('serviceKey', 0) as string);
    const serviceId = optionalServiceId(
      apiCredential?.serviceId ?? (this.getNodeParameter('serviceId', 0) as string),
    );
    const estimatedUnits = this.getNodeParameter('estimatedUnits', 0) as number;

    const result = await estimateUsage({
      baseUrl: coreApiUrl,
      serviceKey,
      serviceId,
      serviceSecret,
      estimatedUnits,
    });

    const output: IDataObject = result
      ? { ...(result as unknown as IDataObject), tollaraOk: true }
      : {
          tollaraOk: false,
          tollaraErrorCode: 'HTTP_ERROR',
          tollaraErrorMessage: 'Usage estimate failed or was denied',
        };

    return [[{ json: output }]];
  }
}
