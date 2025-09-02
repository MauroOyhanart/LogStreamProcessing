data "aws_ssm_parameter" "al2023_x86_64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-x86_64" /* will probably break in a couple of days*/
}

locals {
  ami_id = data.aws_ssm_parameter.al2023_x86_64.value
}

resource "aws_security_group" "ec2_sg" {
  name_prefix = "${var.instance_name}-sg"
  description = "Security group for ${var.instance_name}"
  vpc_id      = var.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = var.tags
}

resource "aws_instance" "ec2_instance" {
  ami                    = local.ami_id
  instance_type          = var.instance_type
  vpc_security_group_ids = [aws_security_group.ec2_sg.id]
  subnet_id              = var.subnet_id
  iam_instance_profile = aws_iam_instance_profile.ec2_profile.name
  associate_public_ip_address = true
  metadata_options { http_tokens = "required" }

  user_data = templatefile("${path.module}/user_data.sh", { instance_name = var.instance_name })

  tags = merge(var.tags, {
    Name = var.instance_name
  })

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    delete_on_termination = true
  }
}


### IAM

resource "aws_iam_role" "ec2_role" {
  name               = "${var.tags.Name}-ec2-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action   = "sts:AssumeRole"
    }]
  })
  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_policy" "app_permissions" {
  name   = "${var.tags.Name}-app-policy"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Kinesis: polling consumer on a single stream
      {
        Sid    = "KinesisReadSingleStream"
        Effect = "Allow"
        Action = [
          "kinesis:DescribeStream",
          "kinesis:DescribeStreamSummary",
          "kinesis:ListShards",
          "kinesis:GetShardIterator",
          "kinesis:GetRecords",
          "kinesis:SubscribeToShard"
        ]
        Resource = var.kinesis_stream_arn
      },

      # DynamoDB: KCL lease table + your app logs table
      {
        Sid    = "DynamoAppTableRW"
        Effect = "Allow"
        Action = [
          "dynamodb:DescribeTable",
          "dynamodb:PutItem",
          "dynamodb:GetItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Scan",
          "dynamodb:Query",
          "dynamodb:BatchWriteItem"
        ]
        Resource = [
          var.aws_dynamodb_table_logs_arn
        ]
      },
      {
        Sid    = "DynamoLeaseTableRW"
        Effect = "Allow"
        Action = [
          "dynamodb:DescribeTable",
          "dynamodb:PutItem",
          "dynamodb:GetItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Scan",
          "dynamodb:Query",
          "dynamodb:BatchWriteItem",
          "dynamodb:UpdateTable"
        ]
        Resource = [
          var.aws_dynamodb_table_kcl_lease_arn,
          var.aws_dynamodb_table_kcl_coordinator_state_arn,
          var.aws_dynamodb_table_kcl_worker_metrics_stats_arn,
          var.aws_dynamodb_index_lease_owner_arn
        ]
      },
      # Allow creating the lease table *only* with the exact name you expect
      {
        Sid    = "DynamoCreateLeaseTableByNameOnly"
        Effect = "Allow"
        Action = [
          "dynamodb:CreateTable"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "dynamodb:RequestedTableName" = [
              var.aws_dynamodb_table_kcl_lease_name
            ]
          }
        }
      },
      # Create ONLY the CoordinatorState table by ARN
      {
        Sid    = "DynamoCreateCoordinatorStateByArn"
        Effect = "Allow"
        Action = "dynamodb:CreateTable"
        Resource = [
          "arn:aws:dynamodb:${var.region}:${var.account_id}:table/${var.stream_name}-consumer-CoordinatorState",
          "arn:aws:dynamodb:${var.region}:${var.account_id}:table/${var.stream_name}-consumer-WorkerMetricStats"
        ]
      },

      # CloudWatch metrics (tighten to known namespaces; expand if you use others)
      {
        Sid    = "CloudWatchPutMetricDataRestricted"
        Effect = "Allow"
        Action = [
          "cloudwatch:PutMetricData"
        ]
        Resource = "*"
        Condition = {
          "StringEquals" = {
            "cloudwatch:namespace" = [
              "KinesisClientLibrary",
              "AWS/Kinesis"
            ]
          }
        }
      },

      # EventBridge limited to one bus
      {
        Sid    = "EventBridgePutEventsOneBus"
        Effect = "Allow"
        Action = [
          "events:PutEvents"
        ]
        Resource = var.event_bus_arn
      },
      { "Sid": "CloudWatchPutMetrics",
        "Effect": "Allow",
        "Action": "cloudwatch:PutMetricData",
        "Resource": "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "app_permissions_attach" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = aws_iam_policy.app_permissions.arn
}

resource "aws_iam_instance_profile" "ec2_profile" {
  name = "${var.tags.Name}-instance-profile-new"
  role = aws_iam_role.ec2_role.name
}
