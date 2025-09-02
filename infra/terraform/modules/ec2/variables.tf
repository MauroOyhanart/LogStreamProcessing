variable "instance_name" {
  description = "Name of the EC2 instance"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.small"
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

variable aws_dynamodb_table_logs_arn {}
variable aws_dynamodb_table_logs_name {}
variable aws_dynamodb_table_kcl_lease_arn {}
variable aws_dynamodb_table_kcl_lease_name {}
variable common_tags {
  type = map(string)
  default = {}
}

variable vpc_id {}
variable subnet_id {}
variable kinesis_stream_arn {
  type = string
}

variable event_bus_arn {
  type = string
}

variable "aws_dynamodb_table_kcl_coordinator_state_arn" {
  type = string
}

variable "aws_dynamodb_table_kcl_worker_metrics_stats_arn" {
  type = string
}

variable "aws_dynamodb_index_lease_owner_arn" {
  type = string
}

variable "region" {
  type = string
}

variable "account_id" {
  type = string
}

variable "stream_name" {
  type = string
}
