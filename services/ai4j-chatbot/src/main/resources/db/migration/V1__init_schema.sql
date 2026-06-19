CREATE TABLE t_model_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT uk_model_provider_name UNIQUE (name)
);

CREATE TABLE t_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    provider_id BIGINT NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    parameters JSON,
    version VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_model_config_provider FOREIGN KEY (provider_id) REFERENCES t_model_provider (id)
);

CREATE TABLE t_model_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    provider_id BIGINT NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    enabled BIT(1) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_model_credential_provider FOREIGN KEY (provider_id) REFERENCES t_model_provider (id)
);

CREATE TABLE t_user_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    credential_id BIGINT NOT NULL,
    config_id BIGINT NOT NULL,
    alias VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_user_config_credential FOREIGN KEY (credential_id) REFERENCES t_model_credential (id),
    CONSTRAINT fk_user_config_config FOREIGN KEY (config_id) REFERENCES t_model_config (id)
);
