import type { ICredentialType, INodeProperties } from 'n8n-workflow';

export class TollaraApi implements ICredentialType {
  name = 'tollaraApi';

  displayName = 'Tollara API';

  documentationUrl = 'https://github.com/tollara/tollara-sdk';

  properties: INodeProperties[] = [
    {
      displayName: 'Service Secret',
      name: 'serviceSecret',
      type: 'string',
      typeOptions: { password: true },
      default: '',
      required: true,
      description: 'Service secret for HMAC signing and verification',
    },
    {
      displayName: 'Service ID',
      name: 'serviceId',
      type: 'string',
      default: '',
      placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
      description:
        'Optional. Your service UUID from the Tollara Service Workspace (open your service → Settings). Used by Validate Key when not overridden on the node.',
    },
    {
      displayName: 'API URL',
      name: 'apiUrl',
      type: 'string',
      default: '',
      placeholder: 'https://api.tollara.ai',
      description: 'Leave blank for production. Default base URL for all Tollara services.',
    },
    {
      displayName:
        'Optional service URL overrides fall back to API URL when blank, then to the production default.',
      name: 'urlOverrideHint',
      type: 'notice',
      default: '',
    },
    {
      displayName: 'Core API URL',
      name: 'coreApiUrl',
      type: 'string',
      default: '',
      placeholder: 'http://host.docker.internal:8081',
      description: 'Optional. Used by Validate Key and Estimate Usage.',
    },
    {
      displayName: 'Usage API URL',
      name: 'usageApiUrl',
      type: 'string',
      default: '',
      placeholder: 'http://host.docker.internal:8084',
      description: 'Optional. Used by Report Usage.',
    },
    {
      displayName: 'Gateway API URL',
      name: 'gatewayApiUrl',
      type: 'string',
      default: '',
      placeholder: 'http://host.docker.internal:8083',
      description: 'Optional. Used by Invoke, Job Status, and Job Result.',
    },
  ];
}
