/**
 * TypeScript models for SPARQi AI Assistant integration
 */

export interface SparqiMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

export interface SparqiContext {
  routeId: string;
  currentTemplate: string;
  routeDescription: string;
  graphMartUri: string;
  layerUris: string[];
  datasourceUrl: string;
  ontologyElementCount: number;
}

export interface SparqiSession {
  sessionId: string;
  userId: string;
  routeId: string;
  createdAt: Date;
  welcomeMessage?: string;
}

export interface SessionStorage {
  sessionId: string;
  userId: string;
  createdAt: string;
  routeId: string;
}

export interface HealthResponse {
  status: string;
  activeSessions: number;
}

export interface SessionResponse {
  sessionId: string;
  routeId: string;
  userId: string;
  createdAt: Date;
  welcomeMessage: string;
}

export interface MessageResponse {
  role: string;
  content: string;
  timestamp: Date;
}

export interface SparqiMetricRecord {
  id: number;
  timestamp: Date;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  messageCount: number;
  sessionId?: string;
  userId?: string;
  routeId?: string;
  modelName: string;
  toolCallCount: number;
  estimatedCost: number;
}

export interface SparqiMetricsSummary {
  totalMessages: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
  totalEstimatedCost: number;
  totalSessions: number;
  avgTokensPerMessage: number;
  avgCostPerMessage: number;
  periodStart: Date;
  periodEnd: Date;
}
