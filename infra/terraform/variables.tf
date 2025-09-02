variable "project_name" {
  description = "Project name for tagging"
  type        = string
  default     = "log-stream-processing"
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-2"
}

variable "stream_name" {
  description = "The name of the AWS Kinesis Data stream"
  default = "log-stream"
}
variable "kcl_app_name" {
  description = "KCL application name used for the lease table"
  type        = string
  default     = "log-stream-consumer"
}

variable "account_id" {
  type = string
  default = "complete"
}
