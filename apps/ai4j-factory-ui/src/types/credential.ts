export interface ModelProvider {
  id: number;
  name: string;
  code: string;
  description: string;
  icon: string;
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

export interface CredentialRequest {
  providerId: number;
  apiKey: string;
}
