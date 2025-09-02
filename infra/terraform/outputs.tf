output "instance_id_1" {
  value = module.ec2_instance_1.instance_id
}

output "instance_id_2" {
  value = module.ec2_instance_2.instance_id
}

output "public_ip_1" {
  value = module.ec2_instance_1.public_ip
}

output "public_ip_2" {
  value = module.ec2_instance_2.public_ip
}

output "vpc_id" {
  value = module.vpc.my_vpc_id
}

output "dynamodb_table" {
  value = module.dynamodb.aws_dynamodb_table_logs_name
}


# Convenience: environment variables needed by the app
output "env_vars" {
  value = {
    AWS_REGION           = var.region
    KINESIS_STREAM       = "log-stream"        # producer stream (external to this stack)
    KCL_APP_NAME         = var.kcl_app_name
    DDB_TABLE            = module.dynamodb.aws_dynamodb_table_logs_name
    EVENT_BUS            = "default"
    EVENTBRIDGE_REQUIRED = "false"
  }
}

# Ready-to-copy .env content
output "env_file" {
  value = <<-EOT
AWS_REGION=${var.region}
KINESIS_STREAM=log-stream
KCL_APP_NAME=${var.kcl_app_name}
DDB_TABLE=${module.dynamodb.aws_dynamodb_table_logs_name}
EVENT_BUS=default
EVENTBRIDGE_REQUIRED=false
EOT
}
