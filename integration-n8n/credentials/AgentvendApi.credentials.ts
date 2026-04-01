import type { ICredentialType, INodeProperties } from 'n8n-workflow';

export class AgentvendApi implements ICredentialType {
  name = 'agentvendApi';

  displayName = 'AgentVend API';

  documentationUrl = 'https://github.com/agentvend/agentvend-sdk';

  properties: INodeProperties[] = [
    {
      displayName: 'Agent Secret',
      name: 'agentSecret',
      type: 'string',
      typeOptions: { password: true },
      default: '',
      required: true,
      description: 'Agent secret for HMAC signing and verification',
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
      default: 'https://api.agentvend.api',
      placeholder: 'https://api.agentvend.api',
      description: 'AgentVend API origin (validate key, usage, polling); defaults to production',
    },
  ];
}
