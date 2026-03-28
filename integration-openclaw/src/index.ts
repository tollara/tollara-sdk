/**
 * OpenClaw plugin for AgentVend.
 * Mode A (caller): tools to invoke agents.
 * Mode B (backend): helpers to verify HMAC and report usage (use with your HTTP server).
 */

import { verifySignatureFromHeaders, getUserContext, reportUsage } from '@agentvend/agent-sdk';

export { verifySignatureFromHeaders, getUserContext, reportUsage };
export { callAgent } from './callAgent';
export { verifyRequest, reportUsageIfNeeded } from './backendHandler';
export type { PluginConfig, CallAgentParams } from './types';
export type { IncomingRequest, BackendResult } from './backendHandler';
