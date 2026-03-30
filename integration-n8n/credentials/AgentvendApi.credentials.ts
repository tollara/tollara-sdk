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
      displayName: 'Core Service URL',
      name: 'coreServiceUrl',
      type: 'string',
      default: 'http://localhost:8081/api/v1',
      placeholder: 'https://core.example.com/api/v1',
      description: 'Base URL of the core service (for Validate Key)',
    },
    {
      displayName: 'Usage Service URL',
      name: 'usageServiceUrl',
      type: 'string',
      default: 'http://localhost:8084',
      placeholder: 'https://usage.example.com',
      description: 'Base URL of the usage service (for Report, Progress, Complete)',
    },
  ];
}
