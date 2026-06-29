// ─── Generic API envelope (matches cms-common ApiResponse<T>) ──────────────
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ErrorResponse;
  meta?: Record<string, unknown>;
}

export interface ErrorResponse {
  code: string;
  message: string;
  details?: string[];
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

// ─── Auth / IAM ──────────────────────────────────────────────────────────────
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface JwtClaims {
  sub: string;           // userId
  email: string;
  tid: string;           // tenantId
  tcode: string;         // tenantCode
  permissions: string[];
  exp: number;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  roles: Role[];
}

export interface Role {
  id: string;
  name: string;
  description: string;
}

export interface Tenant {
  id: string;
  name: string;
  code: string;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  createdAt: string;
}

// ─── Schema Service ──────────────────────────────────────────────────────────
export type FieldType = "TEXT" | "RICHTEXT" | "NUMBER" | "DATE" | "BOOLEAN" | "REFERENCE";

export interface FieldDefinition {
  id: string;
  name: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  sortOrder: number;
  options?: Record<string, unknown>;
  createdAt: string;
}

export interface ContentType {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  description?: string;
  isVersioned: boolean;
  isPublishable: boolean;
  createdAt: string;
  fieldDefinitions: FieldDefinition[];
}

export interface CreateContentTypeRequest {
  code: string;
  name: string;
  description?: string;
  isVersioned: boolean;
  isPublishable: boolean;
}

export interface CreateFieldDefinitionRequest {
  name: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  sortOrder: number;
}

// ─── Content Service ─────────────────────────────────────────────────────────
export interface ContentItem {
  id: string;
  tenantId: string;
  contentTypeId: string;
  contentTypeCode: string;
  status: "DRAFT" | "REVIEW" | "PUBLISHED" | "ARCHIVED";
  payload: Record<string, unknown>;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateContentItemRequest {
  contentTypeCode: string;
  payload: Record<string, unknown>;
}

// ─── Media Service ───────────────────────────────────────────────────────────
export interface MediaFile {
  id: string;
  tenantId: string;
  originalName: string;
  storedName: string;
  mimeType: string;
  size: number;
  url: string;
  alt?: string;
  tags?: string[];
  metadata?: Record<string, string>;
  uploadedAt: string;
}

// ─── Workflow Service ────────────────────────────────────────────────────────
export interface WorkflowDefinition {
  id: string;
  code: string;
  name: string;
  contentTypeCode: string;
  states: string[];
  transitions: WorkflowTransition[];
  createdAt: string;
}

export interface WorkflowTransition {
  from: string;
  to: string;
  requiredPermission: string;
}

export interface WorkflowInstance {
  id: string;
  contentItemId: string;
  definitionCode: string;
  currentState: string;
  createdAt: string;
}

// ─── Form Service ─────────────────────────────────────────────────────────────
export interface FormDefinition {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  description?: string;
  schema: string;          // JSON Schema string
  uiSchema: string;        // UI Schema string
  submitAction?: string;
  isActive: boolean;
  version: number;
  createdAt: string;
}

export interface CreateFormDefinitionRequest {
  code: string;
  name: string;
  description?: string;
  schema: string;
  uiSchema: string;
  submitAction?: string;
}

// ─── Audit Service ───────────────────────────────────────────────────────────
export interface AuditLog {
  id: string;
  tenantId: string;
  actorId?: string;
  actorType: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  outcome: "SUCCESS" | "FAILURE";
  ipAddress?: string;
  createdAt: string;
}

// ─── Search Service ──────────────────────────────────────────────────────────
export interface SearchRequest {
  query: string;
  indexName: string;
  from?: number;
  size?: number;
}

export interface SearchResult {
  id: string;
  index: string;
  score: number;
  source: Record<string, unknown>;
}
