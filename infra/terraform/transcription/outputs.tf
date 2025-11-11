output "s3_bucket_name" {
  description = "Name of the transcript storage bucket."
  value       = aws_s3_bucket.transcripts.bucket
}

output "s3_bucket_arn" {
  description = "ARN of the transcript storage bucket."
  value       = aws_s3_bucket.transcripts.arn
}

output "kms_key_arn" {
  description = "ARN of the KMS key used for transcript encryption."
  value       = aws_kms_key.transcript.arn
}

output "kms_alias_name" {
  description = "Alias associated with the transcript KMS key."
  value       = aws_kms_alias.transcript.name
}

output "application_role_arn" {
  description = "IAM role ARN that the transcription client should assume."
  value       = aws_iam_role.app_transcribe.arn
}

output "indexer_role_arn" {
  description = "IAM role ARN for the downstream indexing Lambda (if created)."
  value       = try(aws_iam_role.indexer_lambda[0].arn, null)
}

output "s3_vpc_endpoint_id" {
  description = "ID of the optional S3 VPC endpoint."
  value       = try(aws_vpc_endpoint.s3[0].id, null)
}

