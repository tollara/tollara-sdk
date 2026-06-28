import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { estimateUsage } from '@tollara/service-sdk';
import { optionalServiceId, requireServiceSecret, resolveCoreApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { optionalServiceIdNotice, serviceIdNodeProperty, serviceSecretNodeProperty, tollaraCoreEndpointProperties } from '../../lib/nodeProperties';

export class TollaraEstimateUsage implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Estimate Usage',
    name: 'tollaraEstimateUsage',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Estimate usage cost and quota for a service key',
    defaults: { name: 'Tollara Estimate Usage' },
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
    const serviceSecret = requireServiceSecret(this.getNodeParameter('serviceSecret', 0) as string);
    const coreApiUrl = resolveCoreApiUrl(credentialsParsed);
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const serviceId = optionalServiceId(this.getNodeParameter('serviceId', 0) as string);
    const estimatedUnits = this.getNodeParameter('estimatedUnits', 0) as number;

    const result = await estimateUsage({
      baseUrl: coreApiUrl,
      serviceKey,
      serviceId,
      serviceSecret,
      estimatedUnits,
    });

    if (!result) {
      throw new Error('Usage estimate failed or was denied');
    }

    return [[{ json: result as unknown as IDataObject }]];
  }
}
