output aws_dynamodb_table_kcl_lease_arn {
  value = aws_dynamodb_table.kcl_lease.arn
}

output aws_dynamodb_table_kcl_lease_name {
  value = aws_dynamodb_table.kcl_lease.name
}

output aws_dynamodb_table_logs_arn {
  value = aws_dynamodb_table.logs.arn
}

output aws_dynamodb_table_logs_name {
  value = aws_dynamodb_table.logs.name
}

# More outputs that are not generated from this configuration BUT that KCL will create and we'll need.

output aws_dynamodb_table_kcl_coordinator_state_arn {
  value = "arn:aws:dynamodb:${var.region}:${var.account_id}:table/${var.stream_name}-consumer-CoordinatorState"
}

output aws_dynamodb_table_kcl_worker_metrics_stats_arn {
  value = "arn:aws:dynamodb:${var.region}:${var.account_id}:table/${var.stream_name}-consumer-WorkerMetricStats"
}

output aws_dynamodb_index_lease_owner_arn {
  value = "arn:aws:dynamodb:${var.region}:${var.account_id}:table/${var.stream_name}-consumer/index/LeaseOwnerToLeaseKeyIndex"
}
