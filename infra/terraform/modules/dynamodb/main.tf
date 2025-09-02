
resource "aws_dynamodb_table" "kcl_lease" {
  name         = var.kcl_app_name
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "leaseKey"

  attribute {
    name = "leaseKey"
    type = "S"
  }

  tags = merge(var.common_tags, { Purpose = "kcl-lease" })
}

resource "aws_dynamodb_table" "logs" {
  name         = "app-logs"
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "pk"
  range_key = "sk"

  attribute {
    name = "pk"
    type = "S"
  }
  attribute {
    name = "sk"
    type = "S"
  }

  tags = merge(var.common_tags, { Purpose = "logs" })
}
