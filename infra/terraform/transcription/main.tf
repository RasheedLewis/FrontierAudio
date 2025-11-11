terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.40.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

locals {
  common_tags = merge(
    {
      Project   = "FrontierAudio"
      Component = "Transcription"
      ManagedBy = "Terraform"
    },
    var.tags
  )
}

# --- KMS key for transcript encryption --------------------------------------

resource "aws_kms_key" "transcript" {
  description             = "KMS key for encrypting Frontier Audio transcript assets"
  deletion_window_in_days = var.kms_deletion_window_days
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "transcript" {
  name          = "alias/${var.kms_alias_name}"
  target_key_id = aws_kms_key.transcript.id
}

# --- S3 bucket for transcripts -----------------------------------------------

resource "aws_s3_bucket" "transcripts" {
  bucket = var.s3_bucket_name

  tags = local.common_tags
}

resource "aws_s3_bucket_versioning" "transcripts" {
  bucket = aws_s3_bucket.transcripts.id

  versioning_configuration {
    status = var.enable_bucket_versioning ? "Enabled" : "Suspended"
  }
}

resource "aws_s3_bucket_public_access_block" "transcripts" {
  bucket = aws_s3_bucket.transcripts.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "transcripts" {
  bucket = aws_s3_bucket.transcripts.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.transcript.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "transcripts" {
  bucket = aws_s3_bucket.transcripts.id

  rule {
    id     = "archive-then-expire"
    status = "Enabled"

    transition {
      days          = var.archive_after_days
      storage_class = var.archive_storage_class
    }

    expiration {
      days = var.delete_after_days
    }
  }
}

# --- IAM roles & policies ----------------------------------------------------

data "aws_iam_policy_document" "app_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = var.app_principal_type
      identifiers = var.app_principal_identifiers
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "app_transcribe" {
  name               = "${var.resource_prefix}-transcribe-app-role"
  assume_role_policy = data.aws_iam_policy_document.app_assume_role.json

  tags = local.common_tags
}

data "aws_iam_policy_document" "app_transcribe" {
  statement {
    effect = "Allow"
    actions = [
      "transcribe:StartStreamTranscription",
      "transcribe:StartCallAnalyticsStreamTranscription",
      "transcribe:GetVocabulary"
    ]
    resources = ["*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:AbortMultipartUpload",
      "s3:GetObject",
      "s3:GetObjectTagging",
      "s3:PutObjectTagging"
    ]
    resources = [
      "${aws_s3_bucket.transcripts.arn}/*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:ListBucket",
      "s3:GetBucketLocation"
    ]
    resources = [
      aws_s3_bucket.transcripts.arn
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey"
    ]
    resources = [
      aws_kms_key.transcript.arn
    ]
  }
}

resource "aws_iam_policy" "app_transcribe" {
  name   = "${var.resource_prefix}-transcribe-app-policy"
  policy = data.aws_iam_policy_document.app_transcribe.json
}

resource "aws_iam_role_policy_attachment" "app_transcribe" {
  role       = aws_iam_role.app_transcribe.name
  policy_arn = aws_iam_policy.app_transcribe.arn
}

# Optional Lambda role for downstream indexing (Jarvis will consume later)
resource "aws_iam_role" "indexer_lambda" {
  count              = var.create_indexer_role ? 1 : 0
  name               = "${var.resource_prefix}-transcript-indexer-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = local.common_tags
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

data "aws_iam_policy_document" "indexer_policy" {
  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:GetObjectTagging"
    ]
    resources = ["${aws_s3_bucket.transcripts.arn}/*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "dynamodb:PutItem",
      "dynamodb:UpdateItem"
    ]
    resources = var.indexer_dynamodb_table_arns
  }

  statement {
    effect = "Allow"
    actions = [
      "es:ESHttpPost",
      "es:ESHttpPut"
    ]
    resources = var.indexer_opensearch_index_arns
  }

  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["arn:aws:logs:${var.aws_region}:${var.aws_account_id}:*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey"
    ]
    resources = [aws_kms_key.transcript.arn]
  }
}

resource "aws_iam_policy" "indexer_policy" {
  count  = var.create_indexer_role ? 1 : 0
  name   = "${var.resource_prefix}-transcript-indexer-policy"
  policy = data.aws_iam_policy_document.indexer_policy.json
}

resource "aws_iam_role_policy_attachment" "indexer_policy" {
  count      = var.create_indexer_role ? 1 : 0
  role       = aws_iam_role.indexer_lambda[0].name
  policy_arn = aws_iam_policy.indexer_policy[0].arn
}

# --- Optional S3 VPC endpoint ------------------------------------------------

resource "aws_vpc_endpoint" "s3" {
  count             = length(var.s3_endpoint_route_table_ids) > 0 ? 1 : 0
  vpc_id            = var.vpc_id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  route_table_ids   = var.s3_endpoint_route_table_ids
  vpc_endpoint_type = "Gateway"

  tags = merge(local.common_tags, { Name = "${var.resource_prefix}-s3-endpoint" })
}

