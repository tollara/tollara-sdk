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
      description: 'Leave blank for production; override only for local or dev testing',
    },
  ];
}
