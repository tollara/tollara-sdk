import type { ICredentialType, INodeProperties } from 'n8n-workflow';

export class TollaraApi implements ICredentialType {
  name = 'tollaraApi';

  displayName = 'Tollara API';

  documentationUrl = 'https://github.com/tollara/tollara-sdk/tree/master/integration-n8n';

  icon = 'file:tollara.svg' as const;

  properties: INodeProperties[] = [
    {
      displayName: 'Service Secret',
      name: 'serviceSecret',
      type: 'string',
      typeOptions: { password: true },
      default: '',
      description: 'Shared secret for your Tollara service (HMAC signing or verification).',
    },
    {
      displayName: 'Service Key',
      name: 'serviceKey',
      type: 'string',
      typeOptions: { password: true },
      default: '',
      description: 'Optional buyer service key for invoke and validate flows.',
    },
    {
      displayName: 'Service ID',
      name: 'serviceId',
      type: 'string',
      default: '',
      placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
      description: 'Optional service UUID when validation should target a specific service.',
    },
  ];
}
