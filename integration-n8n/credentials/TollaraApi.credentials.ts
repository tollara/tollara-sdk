import type { ICredentialType, INodeProperties } from 'n8n-workflow';

/**
 * Legacy credential type kept so workflows saved with credentials.tollaraApi do not show
 * "Unnamed credential". Tollara nodes no longer declare or require this credential —
 * use Service Secret on each node and Set API Endpoints for local URL overrides.
 */
export class TollaraApi implements ICredentialType {
  name = 'tollaraApi';

  displayName = 'Tollara Environment';

  documentationUrl = 'https://www.npmjs.com/package/n8n-nodes-tollara';

  properties: INodeProperties[] = [
    {
      displayName:
        'Tollara nodes no longer use n8n credentials. Set **Service Secret** on each node. You can delete this credential after re-importing workflows.',
      name: 'legacyNotice',
      type: 'notice',
      default: '',
    },
    {
      displayName: 'Set API Endpoints (legacy — prefer node setting)',
      name: 'setApiEndpoints',
      type: 'boolean',
      default: false,
      description: 'Deprecated. Enable **Set API Endpoints** on Tollara nodes instead.',
    },
    {
      displayName: 'Usage API URL',
      name: 'usageApiUrl',
      type: 'string',
      default: '',
      placeholder: 'http://host.docker.internal:8084',
      displayOptions: { show: { setApiEndpoints: [true] } },
    },
    {
      displayName: 'Core API URL',
      name: 'coreApiUrl',
      type: 'string',
      default: '',
      placeholder: 'http://host.docker.internal:8081',
      displayOptions: { show: { setApiEndpoints: [true] } },
    },
    {
      displayName: 'Gateway API URL',
      name: 'gatewayApiUrl',
      type: 'string',
      default: '',
      placeholder: 'http://host.docker.internal:8083',
      displayOptions: { show: { setApiEndpoints: [true] } },
    },
  ];
}
