import type { IExecuteFunctions, INodeExecutionData, INodeType, INodeTypeDescription, IDataObject } from 'n8n-workflow';
import { reportUsage } from '../../lib/tollaraSdk';
import { requireServiceId, requireUsageApiUrlWhenEndpointsEnabled, resolveServiceSecret, resolveUsageApiUrl, tollaraCredentialsFromNodeParameters } from '../../lib/tollaraCredentials';
import { serviceIdNodeProperty, serviceSecretNodeProperty, tollaraUsageEndpointProperties } from '../../lib/nodeProperties';
import { TOLLARA_DOCUMENTATION_URL, tollaraOptionalCredential } from '../../lib/tollaraConstants';

export class TollaraReportUsage implements INodeType {
  description: INodeTypeDescription = {
    displayName: 'Tollara Report Usage',
    name: 'tollaraReportUsage',
    icon: 'file:tollara.svg',
    usableAsTool: true,
    group: ['transform'],
    version: 1,
    description: 'Report usage units for a user and service',
    documentationUrl: TOLLARA_DOCUMENTATION_URL,
    defaults: { name: 'Tollara Report Usage' },
    credentials: [tollaraOptionalCredential],
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
    const serviceSecret = await resolveServiceSecret(this, this.getNodeParameter('serviceSecret', 0) as string);
    requireUsageApiUrlWhenEndpointsEnabled(this);
    const usageApiUrl = resolveUsageApiUrl(credentialsParsed);
    const userId = this.getNodeParameter('userId', 0) as string;
    const serviceId = requireServiceId(this.getNodeParameter('serviceId', 0) as string);
    const unitsUsed = this.getNodeParameter('unitsUsed', 0) as number;

    try {
      const result = await reportUsage({
        baseUrl: usageApiUrl,
        userId,
        serviceId,
        unitsUsed,
        serviceSecret,
      });

      return [[{
        json: {
          ...(result as IDataObject),
          reportSuccess: true,
        },
      }]];
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Usage report failed';
      const statusMatch = message.match(/Usage report failed: (\d+)/);
      return [[{
        json: {
          reportSuccess: false,
          reportHttpStatus: statusMatch ? Number(statusMatch[1]) : 0,
          reportError: message,
        },
      }]];
    }
  }
}
