/**
 * OpenClaw plugin for Agent Hub (Marketplace).
 * Mode A (caller): tools to invoke agents.
 * Mode B (backend): helpers to verify HMAC and report usage (use with your HTTP server).
 */

import { verifySignature, getUserContext, reportUsage } from '@marketplace/agent-sdk';

export { verifySignature, getUserContext, reportUsage };
export { callAgent } from './callAgent';
export { verifyRequest, reportUsageIfNeeded } from './backendHandler';
export type { PluginConfig, CallAgentParams } from './types';
export type { IncomingRequest, BackendResult } from './backendHandler';
