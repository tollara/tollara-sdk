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
  description: 'Your service UUID from the Tollara Service Workspace.',
};

export const optionalServiceIdNotice: INodeProperties = {
  displayName:
    'Service ID is recommended but optional. Leave blank to infer from the service key, or set to pin validation to a specific service.',
  name: 'optionalServiceIdNotice',
  type: 'notice',
  default: '',
};

const productionUrlsNotice: INodeProperties = {
  displayName:
    'Optionally set API Endpoints to override default Tollara URLs. Default production Tollara URLs are used automatically.',
  name: 'productionUrlsNotice',
  type: 'notice',
  default: '',
};

const setApiEndpointsNodeProperty: INodeProperties = {
  displayName: 'Set API Endpoints',
  name: 'setApiEndpoints',
  type: 'boolean',
  default: false,
  description: 'Override default Tollara API URLs when enabled.',
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
  'Optional. Required when Set API Endpoints is enabled. Used by Validate Key and Estimate Usage.',
);

const usageApiUrlNodeProperty = urlNodeProperty(
  'Usage API URL',
  'usageApiUrl',
  'Used by Report Usage and to rewrite progress/complete URLs when gateway returns production hosts. Required when Set API Endpoints is enabled.',
);

const gatewayApiUrlNodeProperty = urlNodeProperty(
  'Gateway API URL',
  'gatewayApiUrl',
  'Optional. Used by Invoke, Job Status, and Job Result. Required when Set API Endpoints is enabled.',
);

/** Nodes that call core (Validate Key, Estimate Usage). */
export const tollaraCoreEndpointProperties: INodeProperties[] = [
  productionUrlsNotice,
  setApiEndpointsNodeProperty,
  coreApiUrlNodeProperty,
];

/** Nodes that call usage / rewrite progress-complete URLs (Progress, Complete, Report Usage). */
export const tollaraUsageEndpointProperties: INodeProperties[] = [
  productionUrlsNotice,
  setApiEndpointsNodeProperty,
  usageApiUrlNodeProperty,
];

/** Nodes that call gateway (Invoke, Job Status, Job Result). */
export const tollaraGatewayEndpointProperties: INodeProperties[] = [
  productionUrlsNotice,
  setApiEndpointsNodeProperty,
  gatewayApiUrlNodeProperty,
];
