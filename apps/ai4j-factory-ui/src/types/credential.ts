export interface ModelProvider {
  id: number;
  name: string;
  baseUrl?: string;
  code?: string;
  description: string;
  icon?: string;
}

export interface ModelCredential {
  id: number;
  userId: string;
  provider: ModelProvider;
  apiKey: string;
  status: 'VALID' | 'INVALID' | 'EXPIRED';
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ModelConfig {
  id: number;
  name: string;
  provider: ModelProvider;
  modelName: string;
  parameters: Record<string, unknown> | null;
  version: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SelectableModelOption {
  id: string;
  credentialId: number;
  modelName: string;
  provider: ModelProvider;
}

export interface CredentialRequest {
  providerId: number;
  apiKey: string;
}
