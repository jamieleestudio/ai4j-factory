import { CredentialRequest, ModelCredential, ModelProvider } from "../types/credential";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';
const DEFAULT_USER_ID = 'default-user'; // TODO: Get from auth context

const headers = {
  'Content-Type': 'application/json',
  'X-User-Id': DEFAULT_USER_ID,
};

export const credentialService = {
  // Credentials
  getCredentials: async (): Promise<ModelCredential[]> => {
    const response = await fetch(`${API_BASE_URL}/credentials`, { headers });
    if (!response.ok) throw new Error('Failed to fetch credentials');
    return response.json();
  },

  addCredential: async (data: CredentialRequest): Promise<ModelCredential> => {
    const response = await fetch(`${API_BASE_URL}/credentials`, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error('Failed to add credential');
    return response.json();
  },

  updateCredential: async (id: number, apiKey: string): Promise<ModelCredential> => {
    const response = await fetch(`${API_BASE_URL}/credentials/${id}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ apiKey }), // Assuming partial update or just apiKey
    });
    if (!response.ok) throw new Error('Failed to update credential');
    return response.json();
  },

  deleteCredential: async (id: number): Promise<void> => {
    const response = await fetch(`${API_BASE_URL}/credentials/${id}`, {
      method: 'DELETE',
      headers,
    });
    if (!response.ok) throw new Error('Failed to delete credential');
  },

  toggleCredentialStatus: async (id: number, enabled: boolean): Promise<ModelCredential> => {
    const response = await fetch(`${API_BASE_URL}/credentials/${id}/status?enabled=${enabled}`, {
      method: 'PATCH',
      headers,
    });
    if (!response.ok) throw new Error('Failed to toggle credential status');
    return response.json();
  },

  // Providers
  getProviders: async (): Promise<ModelProvider[]> => {
    const response = await fetch(`${API_BASE_URL}/providers`, { headers });
    if (!response.ok) throw new Error('Failed to fetch providers');
    return response.json();
  },
};
