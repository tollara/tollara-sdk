import type { INodeProperties } from 'n8n-workflow';

export const serviceSecretNodeProperty: INodeProperties = {
  displayName: 'Service Secret',
  name: 'serviceSecret',
  type: 'string',
  typeOptions: { password: true },
  default: '',
  description:
    'Secret for this Tollara service (HMAC signing or verification). Set on each node when a workflow uses multiple services.',
};

export const serviceIdNodeProperty: INodeProperties = {
  displayName: 'Service ID',
  name: 'serviceId',
  type: 'string',
  default: '',
  placeholder: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
  description: 'Your service UUID from the Tollara Service Workspace (service settings).',
};

const localDevNotice: INodeProperties = {
  displayName:
    'Production uses default Tollara URLs automatically. For local Docker or staging, enable **Set API Endpoints** below.',
  name: 'localDevNotice',
  type: 'notice',
  default: '',
};

const setApiEndpointsNodeProperty: INodeProperties = {
  displayName: 'Set API Endpoints',
  name: 'setApiEndpoints',
  type: 'boolean',
  default: false,
  description:
    'Leave disabled for production Tollara URLs. Enable only for staging, self-hosted, or local development.',
};

function urlNodeProperty(
  displayName: string,
  name: string,
  description: string,
): INodeProperties {
  return {
    displayName,
    name,
    type: 'string',
    default: '',
    placeholder: 'https://api.tollara.ai',
    description,
    displayOptions: {
      show: {
        setApiEndpoints: [true],
      },
    },
  };
}

const coreApiUrlNodeProperty = urlNodeProperty(
  'Core API URL',
  'coreApiUrl',
  'Optional. Used by Validate Key and Estimate Usage.',
);

const usageApiUrlNodeProperty = urlNodeProperty(
  'Usage API URL',
  'usageApiUrl',
  'Optional. Used by Report Usage and to rewrite progress/complete URLs for local usage services.',
);

const gatewayApiUrlNodeProperty = urlNodeProperty(
  'Gateway API URL',
  'gatewayApiUrl',
  'Optional. Used by Invoke, Job Status, and Job Result.',
);

/** Nodes that call core (Validate Key, Estimate Usage). */
export const tollaraCoreEndpointProperties: INodeProperties[] = [
  localDevNotice,
  setApiEndpointsNodeProperty,
  coreApiUrlNodeProperty,
];

/** Nodes that call usage / rewrite progress-complete URLs (Progress, Complete, Report Usage). */
export const tollaraUsageEndpointProperties: INodeProperties[] = [
  localDevNotice,
  setApiEndpointsNodeProperty,
  usageApiUrlNodeProperty,
];

/** Nodes that call gateway (Invoke, Job Status, Job Result). */
export const tollaraGatewayEndpointProperties: INodeProperties[] = [
  localDevNotice,
  setApiEndpointsNodeProperty,
  gatewayApiUrlNodeProperty,
];
