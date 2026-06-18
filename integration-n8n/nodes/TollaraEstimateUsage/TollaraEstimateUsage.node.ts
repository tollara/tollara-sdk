import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { estimateUsage } from '@tollara/service-sdk';
import { getTollaraCredentials } from '../../lib/tollaraCredentials';

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
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Service ID', name: 'serviceId', type: 'string', default: '' },
      { displayName: 'Estimated Units', name: 'estimatedUnits', type: 'number', default: 1, required: true },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const { apiUrl, serviceSecret } = getTollaraCredentials(credentials);
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const serviceId = (this.getNodeParameter('serviceId', 0) as string) || null;
    const estimatedUnits = this.getNodeParameter('estimatedUnits', 0) as number;

    const result = await estimateUsage({
      baseUrl: apiUrl,
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
