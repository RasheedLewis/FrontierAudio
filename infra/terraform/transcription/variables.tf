variable "aws_region" {
  description = "AWS region to deploy resources into."
  type        = string
}

variable "aws_account_id" {
  description = "AWS account number (used for IAM policy resource ARNs)."
  type        = string
}

variable "resource_prefix" {
  description = "Prefix used for naming AWS resources (e.g. frontier-audio-dev)."
  type        = string
}

variable "s3_bucket_name" {
  description = "Name of the S3 bucket that stores encrypted transcripts."
  type        = string
}

variable "kms_alias_name" {
  description = "Alias name (without the alias/ prefix) for the transcript KMS key."
  type        = string
  default     = "frontier-audio-transcripts"
}

variable "kms_deletion_window_days" {
  description = "Number of days before a scheduled KMS key deletion."
  type        = number
  default     = 30
}

variable "archive_after_days" {
  description = "Number of days after which transcripts transition to colder storage."
  type        = number
  default     = 30
}

variable "delete_after_days" {
  description = "Number of days after which transcripts are permanently deleted."
  type        = number
  default     = 365
}

variable "archive_storage_class" {
  description = "Amazon S3 storage class to transition objects into (e.g. GLACIER, DEEP_ARCHIVE)."
  type        = string
  default     = "DEEP_ARCHIVE"
}

variable "enable_bucket_versioning" {
  description = "Whether to enable S3 bucket versioning."
  type        = bool
  default     = true
}

variable "app_principal_type" {
  description = "IAM principal type that will assume the streaming application role (e.g. Service, AWS, Federated)."
  type        = string
  default     = "AWS"
}

variable "app_principal_identifiers" {
  description = "List of principal ARNs or identifiers allowed to assume the streaming application role."
  type        = list(string)
}

variable "create_indexer_role" {
  description = "Whether to create a Lambda execution role for downstream indexing."
  type        = bool
  default     = true
}

variable "indexer_dynamodb_table_arns" {
  description = "List of DynamoDB table ARNs the indexer Lambda can write to."
  type        = list(string)
  default     = []
}

variable "indexer_opensearch_index_arns" {
  description = "List of OpenSearch index ARNs accessible by the indexer Lambda."
  type        = list(string)
  default     = []
}

variable "vpc_id" {
  description = "VPC ID used when creating optional endpoints."
  type        = string
  default     = ""
}

variable "s3_endpoint_route_table_ids" {
  description = "Route table IDs for creating an S3 Gateway VPC endpoint. Leave empty to skip."
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "Additional resource tags."
  type        = map(string)
  default     = {}
}

