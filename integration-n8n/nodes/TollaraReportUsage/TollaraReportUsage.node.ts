import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { reportUsage } from '@tollara/service-sdk';
import { requireServiceId, requireServiceSecret, resolveUsageApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { serviceIdNodeProperty, serviceSecretNodeProperty, tollaraUsageEndpointProperties } from '../../lib/nodeProperties';

export class TollaraReportUsage implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Report Usage',
    name: 'tollaraReportUsage',
    icon: 'file:tollara.png',
    group: ['transform'],
    version: 1,
    description: 'Report usage units for a user and service',
    defaults: { name: 'Tollara Report Usage' },
    inputs: ['main'],
    outputs: ['main'],
    properties: [
      serviceSecretNodeProperty,
      { displayName: 'User ID', name: 'userId', type: 'string', default: '', required: true },
      serviceIdNodeProperty,
      { displayName: 'Units Used', name: 'unitsUsed', type: 'number', default: 1, required: true },
      ...tollaraUsageEndpointProperties,
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentialsParsed = tollaraCredentialsFromNodeParameters(this);
    const serviceSecret = requireServiceSecret(this.getNodeParameter('serviceSecret', 0) as string);
    const usageApiUrl = resolveUsageApiUrl(credentialsParsed);
    const userId = this.getNodeParameter('userId', 0) as string;
    const serviceId = requireServiceId(this.getNodeParameter('serviceId', 0) as string);
    const unitsUsed = this.getNodeParameter('unitsUsed', 0) as number;

    const result = await reportUsage({
      baseUrl: usageApiUrl,
      userId,
      serviceId,
      unitsUsed,
      serviceSecret,
    });

    return [[{ json: result as IDataObject }]];
  }
}
