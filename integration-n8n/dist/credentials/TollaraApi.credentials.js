"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.TollaraApi = void 0;
class TollaraApi {
    constructor() {
        this.name = 'tollaraApi';
        this.displayName = 'Tollara API';
        this.documentationUrl = 'https://github.com/tollara/tollara-sdk/tree/main/integration-n8n';
        this.icon = 'file:tollara-brand.svg';
        this.properties = [
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
        // Validates the entered Service Key against Tollara. A valid key returns
        // { valid: true }; an invalid key returns a non-2xx status or { valid: false }.
        this.test = {
            request: {
                baseURL: 'https://api.tollara.ai',
                url: '/core/api/v1/service-keys/validate',
                method: 'POST',
                body: {
                    serviceKey: '={{$credentials.serviceKey}}',
                    serviceId: '={{$credentials.serviceId}}',
                    serviceSecret: '={{$credentials.serviceSecret}}',
                },
            },
            rules: [
                {
                    type: 'responseSuccessBody',
                    properties: {
                        key: 'valid',
                        value: false,
                        message: 'Service key is invalid or does not grant access',
                    },
                },
            ],
        };
    }
}
exports.TollaraApi = TollaraApi;
