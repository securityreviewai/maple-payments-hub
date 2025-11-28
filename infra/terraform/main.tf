terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source = "hashicorp/archive"
    }
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "maple-payments-hub"
      Environment = var.environment
      ManagedBy   = "terraform"
      Architecture = "Serverless"
    }
  }
}

# --- 1. DATA (Storage & NoSQL) ---


[Image of AWS DynamoDB architecture]


# Replaces RDS: Low latency, auto-scaling NoSQL table
resource "aws_dynamodb_table" "transactions" {
  name           = "${var.project_name}-transactions"
  billing_mode   = "PAY_PER_REQUEST" # Serverless pricing
  hash_key       = "TransactionId"
  range_key      = "Timestamp"

  attribute {
    name = "TransactionId"
    type = "S"
  }

  attribute {
    name = "Timestamp"
    type = "S"
  }

  # Global Secondary Index for querying by User
  attribute {
    name = "UserId"
    type = "S"
  }

  global_secondary_index {
    name               = "UserIndex"
    hash_key           = "UserId"
    projection_type    = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  tags = {
    Name = "${var.project_name}-ddb"
  }
}

# Replaces S3 Files bucket (Kept for audit logs/blobs)
resource "aws_s3_bucket" "audit_logs" {
  bucket = "${var.project_name}-audit-${random_string.suffix.result}"
}

# --- 2. MESSAGING (Event Bus) ---


[Image of AWS EventBridge architecture]


# Replaces MSK (Kafka): Serverless Event Bus
resource "aws_cloudwatch_event_bus" "payments" {
  name = "${var.project_name}-event-bus"
}

# SQS Queue for buffering (Dead Letter Queue functionality)
resource "aws_sqs_queue" "payment_dlq" {
  name = "${var.project_name}-dlq"
}

# --- 3. API GATEWAY (Entry Point) ---

# Replaces ALB: HTTP API Gateway
resource "aws_apigatewayv2_api" "main" {
  name          = "${var.project_name}-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true
}

# --- 4. COMPUTE (Lambda) ---

# ZIP file generation for dummy code (so Terraform plans successfully)
data "archive_file" "lambda_dummy" {
  type        = "zip"
  output_path = "${path.module}/lambda_function_payload.zip"
  
  source_content {
    filename = "index.js"
    content  = "exports.handler = async (event) => { return { statusCode: 200, body: 'Hello from Serverless Maple!' }; };"
  }
}

# IAM Role for Lambda
resource "aws_iam_role" "lambda_exec" {
  name = "${var.project_name}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# Policy to allow Lambda to talk to DynamoDB and EventBridge
resource "aws_iam_policy" "lambda_policy" {
  name = "${var.project_name}-lambda-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = [
          "dynamodb:PutItem",
          "dynamodb:GetItem",
          "dynamodb:Query"
        ]
        Effect   = "Allow"
        Resource = aws_dynamodb_table.transactions.arn
      },
      {
        Action   = ["events:PutEvents"]
        Effect   = "Allow"
        Resource = aws_cloudwatch_event_bus.payments.arn
      },
      {
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Effect   = "Allow"
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_attach" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = aws_iam_policy.lambda_policy.arn
}

# Replaces ECS Task: Core Payment Processor Function
resource "aws_lambda_function" "processor" {
  filename      = data.archive_file.lambda_dummy.output_path
  function_name = "${var.project_name}-processor"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "index.handler"
  runtime       = "nodejs18.x"
  timeout       = 10
  memory_size   = 1024

  source_code_hash = data.archive_file.lambda_dummy.output_base64sha256

  environment {
    variables = {
      TABLE_NAME = aws_dynamodb_table.transactions.name
      EVENT_BUS  = aws_cloudwatch_event_bus.payments.name
    }
  }
}

# --- 5. CONNECTING API TO LAMBDA ---

resource "aws_apigatewayv2_integration" "lambda_integration" {
  api_id           = aws_apigatewayv2_api.main.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.processor.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "payment_route" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /payments"
  target    = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
}

# Allow API Gateway to invoke Lambda
resource "aws_lambda_permission" "api_gw" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.processor.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}

# --- 6. UTILITIES ---

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

# --- 7. OUTPUTS ---

output "api_endpoint" {
  value = aws_apigatewayv2_api.main.api_endpoint
  description = "The HTTP API Gateway Endpoint URL"
}

output "dynamodb_table" {
  value = aws_dynamodb_table.transactions.name
}
