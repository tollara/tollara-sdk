import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { validateServiceKey } from '@tollara/service-sdk';

export class TollaraValidateKey implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Validate Key',
    name: 'tollaraValidateKey',
    icon: 'file:tollara.svg',
    group: ['transform'],
    version: 1,
    description: 'Validate a service key via the core service',
    defaults: { name: 'Tollara Validate Key' },
    inputs: ['main'],
    outputs: ['main'],
    credentials: [{ name: 'tollaraApi', required: true }],
    properties: [
      { displayName: 'Service Key', name: 'serviceKey', type: 'string', typeOptions: { password: true }, default: '', required: true },
      { displayName: 'Service ID', name: 'serviceId', type: 'string', default: '' },
    ],
  };

  async execute(this: IExecuteFunctions): Promise<INodeExecutionData[][]> {
    const credentials = await this.getCredentials('tollaraApi');
    const apiUrl = (credentials as { apiUrl?: string }).apiUrl as string;
    const serviceSecret = (credentials as { serviceSecret?: string }).serviceSecret as string;
    const serviceKey = this.getNodeParameter('serviceKey', 0) as string;
    const serviceId = (this.getNodeParameter('serviceId', 0) as string) || undefined;

    const result = await validateServiceKey({
      baseUrl: apiUrl,
      serviceKey,
      serviceId: serviceId ?? null,
      serviceSecret,
    });

    return [[{ json: (result ?? {}) as IDataObject }]];
  }
}
