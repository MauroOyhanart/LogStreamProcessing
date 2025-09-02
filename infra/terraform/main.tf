terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.79.0"
    }
  }
}

provider "aws" {
  region = var.region
}

################################################################################################
######################## VPC ########################################################################
################################################################################################

module "vpc" {
  source = "./modules/vpc"

  az              = "us-east-2a"
  cidr_block      = "10.0.0.0/16"
  public_subnet   = "10.0.1.0/24"
}

################################################################################################
######################## DynamoDB ########################################################################
################################################################################################

module "dynamodb" {
  source = "./modules/dynamodb"

  kcl_app_name = var.kcl_app_name
  region = var.region
  account_id = var.account_id
  stream_name = var.stream_name
  common_tags = {
    Name        = "LogStreamProcessing-DynamoDB"
    Environment = "development"
    Project     = "portfolio-kinesis-data-streams"
  }
}

################################################################################################
######################## EC2 ########################################################################
################################################################################################

module "ec2_instance_1" {
  source = "./modules/ec2"

  instance_name = "log-stream-processing-1"

  vpc_id                        = module.vpc.my_vpc_id
  subnet_id                     = module.vpc.public_subnet_id
  region = var.region
  account_id = var.account_id

  stream_name = var.stream_name

  tags = {
    Name        = "LogStreamProcessingInstance1"
    Environment = "development"
    Project     = "portfolio-kinesis-data-streams"
  }

  kinesis_stream_arn = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${var.stream_name}"

  # We simplify configuration by putting IAM inside the ec2 module
  aws_dynamodb_table_kcl_lease_arn = module.dynamodb.aws_dynamodb_table_kcl_lease_arn
  aws_dynamodb_table_kcl_lease_name = module.dynamodb.aws_dynamodb_table_kcl_lease_name
  aws_dynamodb_table_logs_arn = module.dynamodb.aws_dynamodb_table_logs_arn
  aws_dynamodb_table_logs_name = module.dynamodb.aws_dynamodb_table_logs_name


  event_bus_arn = "arn:aws:events:${var.region}:${var.account_id}:event-bus/default"
  aws_dynamodb_table_kcl_coordinator_state_arn = module.dynamodb.aws_dynamodb_table_kcl_coordinator_state_arn
  aws_dynamodb_table_kcl_worker_metrics_stats_arn = module.dynamodb.aws_dynamodb_table_kcl_worker_metrics_stats_arn
  aws_dynamodb_index_lease_owner_arn = module.dynamodb.aws_dynamodb_index_lease_owner_arn
}

module "ec2_instance_2" {
  source = "./modules/ec2"

  instance_name = "log-stream-processing-2"

  vpc_id                        = module.vpc.my_vpc_id
  subnet_id                     = module.vpc.public_subnet_id
  region = var.region
  account_id = var.account_id

  stream_name = var.stream_name

  tags = {
    Name        = "LogStreamProcessingInstance2"
    Environment = "development"
    Project     = "portfolio-kinesis-data-streams"
  }

  kinesis_stream_arn = "arn:aws:kinesis:${var.region}:${var.account_id}:stream/${var.stream_name}"

  # We simplify configuration by putting IAM inside the ec2 module
  aws_dynamodb_table_kcl_lease_arn = module.dynamodb.aws_dynamodb_table_kcl_lease_arn
  aws_dynamodb_table_kcl_lease_name = module.dynamodb.aws_dynamodb_table_kcl_lease_name
  aws_dynamodb_table_logs_arn = module.dynamodb.aws_dynamodb_table_logs_arn
  aws_dynamodb_table_logs_name = module.dynamodb.aws_dynamodb_table_logs_name

  event_bus_arn = "arn:aws:events:${var.region}:${var.account_id}:event-bus/default"
  aws_dynamodb_table_kcl_coordinator_state_arn = module.dynamodb.aws_dynamodb_table_kcl_coordinator_state_arn
  aws_dynamodb_table_kcl_worker_metrics_stats_arn = module.dynamodb.aws_dynamodb_table_kcl_worker_metrics_stats_arn
  aws_dynamodb_index_lease_owner_arn = module.dynamodb.aws_dynamodb_index_lease_owner_arn
}
