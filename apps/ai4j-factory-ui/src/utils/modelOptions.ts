import { ModelConfig, ModelCredential, SelectableModelOption } from "../types/credential";

export function buildSelectableModelOptions(
  credentials: ModelCredential[],
  configs: ModelConfig[]
): SelectableModelOption[] {
  return credentials
    .filter((credential) => credential.enabled)
    .flatMap((credential) =>
      configs
        .filter((config) => config.provider.id === credential.provider.id)
        .map((config) => ({
          id: `${credential.id}:${config.id}`,
          credentialId: credential.id,
          modelName: config.modelName,
          provider: credential.provider,
        }))
    );
}
