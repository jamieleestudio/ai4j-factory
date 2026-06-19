'use client';

import React, { useState, useEffect } from 'react';
import { Plus, Trash, Edit, Check, X, Shield, Key } from 'lucide-react';
import { credentialService } from '../services/credentialService';
import { ModelCredential, ModelProvider } from '../types/credential';
import { Modal } from './Modal';
import clsx from 'clsx';

export default function CredentialManager() {
  const [credentials, setCredentials] = useState<ModelCredential[]>([]);
  const [providers, setProviders] = useState<ModelProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCredential, setEditingCredential] = useState<ModelCredential | null>(null);
  
  // Form state
  const [selectedProviderId, setSelectedProviderId] = useState<number | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [creds, provs] = await Promise.all([
        credentialService.getCredentials(),
        credentialService.getProviders()
      ]);
      setCredentials(creds);
      setProviders(provs);
      setError(null);
    } catch (err) {
      setError('Failed to load credentials');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (credential?: ModelCredential) => {
    if (credential) {
      setEditingCredential(credential);
      setSelectedProviderId(credential.provider.id);
      setApiKey(credential.apiKey);
    } else {
      setEditingCredential(null);
      setSelectedProviderId(providers.length > 0 ? providers[0].id : null);
      setApiKey('');
    }
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingCredential(null);
    setApiKey('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProviderId || !apiKey) return;

    try {
      setIsSubmitting(true);
      if (editingCredential) {
        await credentialService.updateCredential(editingCredential.id, apiKey);
      } else {
        await credentialService.addCredential({
          providerId: selectedProviderId,
          apiKey
        });
      }
      await fetchData();
      handleCloseModal();
    } catch (err) {
      setError('Failed to save credential');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Are you sure you want to delete this credential?')) return;
    try {
      await credentialService.deleteCredential(id);
      setCredentials(credentials.filter(c => c.id !== id));
    } catch (err) {
      setError('Failed to delete credential');
    }
  };

  const handleToggleStatus = async (id: number, currentStatus: boolean) => {
    try {
      await credentialService.toggleCredentialStatus(id, !currentStatus);
      setCredentials(credentials.map(c => 
        c.id === id ? { ...c, enabled: !currentStatus } : c
      ));
    } catch (err) {
      setError('Failed to update status');
    }
  };

  if (loading) return <div className="p-4 text-center text-gray-500">Loading credentials...</div>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold flex items-center gap-2">
          <Shield className="w-5 h-5" />
          API Credentials
        </h2>
        <button
          onClick={() => handleOpenModal()}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md transition-colors text-sm font-medium"
        >
          <Plus size={16} />
          Add Credential
        </button>
      </div>

      {error && (
        <div className="p-3 bg-red-100 border border-red-200 text-red-700 rounded-md text-sm">
          {error}
        </div>
      )}

      <div className="grid gap-4">
        {credentials.length === 0 ? (
          <div className="text-center py-8 text-gray-500 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-dashed border-gray-300 dark:border-gray-700">
            No credentials found. Add one to get started.
          </div>
        ) : (
          credentials.map((cred) => (
            <div 
              key={cred.id} 
              className="flex items-center justify-between p-4 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm hover:shadow-md transition-shadow"
            >
              <div className="flex items-center gap-4">
                <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-md">
                  {/* Ideally fetch icon from provider.icon */}
                  <Key className="w-5 h-5 text-gray-600 dark:text-gray-300" />
                </div>
                <div>
                  <h3 className="font-medium text-gray-900 dark:text-gray-100">
                    {cred.provider.name}
                  </h3>
                  <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                    <span className="font-mono bg-gray-100 dark:bg-gray-900 px-1.5 py-0.5 rounded text-xs">
                      {cred.apiKey.substring(0, 4)}...{cred.apiKey.substring(cred.apiKey.length - 4)}
                    </span>
                    <span className={clsx(
                      "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium",
                      cred.enabled ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400" : "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400"
                    )}>
                      {cred.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleToggleStatus(cred.id, cred.enabled)}
                  className={clsx(
                    "p-2 rounded-md transition-colors",
                    cred.enabled 
                      ? "text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20" 
                      : "text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800"
                  )}
                  title={cred.enabled ? "Disable" : "Enable"}
                >
                  {cred.enabled ? <Check size={18} /> : <X size={18} />}
                </button>
                <button
                  onClick={() => handleOpenModal(cred)}
                  className="p-2 text-blue-600 hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20 rounded-md transition-colors"
                  title="Edit"
                >
                  <Edit size={18} />
                </button>
                <button
                  onClick={() => handleDelete(cred.id)}
                  className="p-2 text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20 rounded-md transition-colors"
                  title="Delete"
                >
                  <Trash size={18} />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      <Modal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        title={editingCredential ? "Edit Credential" : "Add New Credential"}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Provider
            </label>
            <select
              value={selectedProviderId || ''}
              onChange={(e) => setSelectedProviderId(Number(e.target.value))}
              disabled={!!editingCredential}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none disabled:opacity-50 disabled:cursor-not-allowed"
              required
            >
              {providers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              API Key
            </label>
            <input
              type="password"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none"
              placeholder="sk-..."
              required
            />
          </div>

          <div className="flex justify-end gap-3 pt-4">
            <button
              type="button"
              onClick={handleCloseModal}
              className="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-md transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting || !selectedProviderId || !apiKey}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSubmitting ? 'Saving...' : 'Save Credential'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
