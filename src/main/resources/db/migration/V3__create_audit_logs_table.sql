-- V3__create_audit_logs_table.sql
-- Creación de la tabla inmutable audit_logs con índices optimizados

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    user_name VARCHAR(100) NOT NULL,
    user_email VARCHAR(100) NOT NULL,
    user_role VARCHAR(50) NOT NULL,
    action_type VARCHAR(30) NOT NULL,  -- LOGIN, CREATE, UPDATE, DELETE, ACCESS_DENIED
    entity_type VARCHAR(50) NOT NULL,  -- PRODUCT, CATEGORY, STORAGE, AUTH
    entity_id VARCHAR(100),
    previous_data TEXT,
    new_data TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Índices para búsqueda rápida y filtrado por GLOBAL_ADMIN
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action_type ON audit_logs(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type ON audit_logs(entity_type);
