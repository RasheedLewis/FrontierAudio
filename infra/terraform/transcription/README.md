# Frontier Audio – Transcription Infra Terraform

This Terraform package provisions the AWS scaffolding required for PR-03:

* KMS key + alias for encrypting transcript assets.
* Private S3 bucket with SSE-KMS, lifecycle rules (archive/delete), and optional versioning.
* IAM role/policy for the transcription client (mobile app or gateway) to access Amazon Transcribe Streaming, S3, and the KMS key.
* Optional Lambda execution role for downstream indexing (DynamoDB / OpenSearch).
* Optional S3 Gateway VPC endpoint.

## Prerequisites

* Terraform ≥ 1.6
* AWS CLI credentials able to create IAM/KMS/S3 resources in the target account.
* (Optional) Existing VPC and route tables if you want the S3 VPC endpoint.

## Files

| File          | Description |
| ------------- | ----------- |
| `main.tf`     | Provider config and resource definitions. |
| `variables.tf`| All tunable settings (region, bucket name, lifecycle days, etc.). |
| `outputs.tf`  | Useful ARNs/IDs for integrating the application stack. |
| `terraform.tfvars.example` | Sample variable file you can copy and customize. |

## Usage

```bash
cd infra/terraform/transcription
cp terraform.tfvars.example terraform.tfvars   # customize values
terraform init
terraform plan
terraform apply
```

Outputs include the transcript bucket name, KMS key ARN, IAM role ARNs, and the optional S3 VPC endpoint ID. Feed those values into the mobile/backend configuration so the Kotlin client can assume the correct role and point to the encrypted bucket.

## Notes

* The module does **not** create Amazon Transcribe resources (the service is serverless). Instead, it ensures you have the IAM permissions and network controls required to call the streaming WebSocket API safely.
* If you do not need the indexing Lambda yet, set `create_indexer_role = false`.
* Provide the correct principal identifier list so only your Cognito/STS identity provider (or backend service) can assume the `app_transcribe` role.
* Adjust lifecycle policies (`archive_after_days`, `delete_after_days`, `archive_storage_class`) to match retention requirements.
* When enabling the S3 Gateway endpoint, supply `vpc_id` and the route table IDs so traffic to S3 stays on the AWS backbone.


