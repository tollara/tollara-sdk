import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { reportUsage } from '@tollara/service-sdk';
import { getTollaraCredentials, resolveUsageApiUrl } from '../../lib/tollaraCredentials';

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
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      { displayName: 'User ID', name: 'userId', type: 'string', default: '', required: true },
      { displayName: 'Service ID', name: 'serviceId', type: 'string', default: '', required: true },
      { displayName: 'Units Used', name: 'unitsUsed', type: 'number', default: 1, required: true },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const credentialsParsed = getTollaraCredentials(credentials);
    const { serviceSecret } = credentialsParsed;
    const usageApiUrl = resolveUsageApiUrl(credentialsParsed);
    const userId = this.getNodeParameter('userId', 0) as string;
    const serviceId = this.getNodeParameter('serviceId', 0) as string;
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
