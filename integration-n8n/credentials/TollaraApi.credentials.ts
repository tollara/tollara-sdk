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
      displayName: 'Gateway URL',
      name: 'gatewayUrl',
      type: 'string',
      default: 'http://localhost:8083',
      placeholder: 'https://gateway.example.com',
      description: 'Base URL of the gateway service (for Invoke)',
    },
    {
      displayName: 'API URL',
      name: 'apiUrl',
      type: 'string',
      default: 'https://api.tollara.ai',
      placeholder: 'https://api.tollara.ai',
      description: 'Tollara API origin (validate key, usage, polling); defaults to production',
    },
  ];
}
