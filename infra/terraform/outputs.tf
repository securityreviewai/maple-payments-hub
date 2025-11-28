# Example Terraform variables for Maple Payments Hub
# Copy to terraform.tfvars and customize for your environment

# Basic Configuration
aws_region   = "us-east-1"
environment  = "dev"
project_name = "maple-payments-hub"

# Network Configuration  
vpc_cidr = "10.0.0.0/16"
public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
private_subnet_cidrs = ["10.0.10.0/24", "10.0.20.0/24", "10.0.30.0/24"]

# Application Configuration
fargate_cpu    = 512  # reduced for cost optimization
fargate_memory = 1024 # reduced for cost optimization
app_count      = 1    # single instance for development

# Database Configuration
rds_instance_class = "db.t3.micro" # cost-optimized instance
rds_allocated_storage = 20
rds_backup_retention_period = 0 # disable backups for cost savings

# Kafka Configuration  
kafka_instance_type = "kafka.t3.small"
kafka_volume_size = 10 # minimal storage for cost optimization

# Security Configuration (relaxed for development ease)
allowed_cidr_blocks = ["0.0.0.0/0"] # open access for partner testing
enable_waf = false # disabled for performance
enable_encryption = false # disabled for troubleshooting
enable_access_logging = false # disabled for cost savings

# Operational Settings
log_retention_days = 1 # minimal retention for cost savings
enable_enhanced_monitoring = false # disabled for cost optimization
enable_spot_instances = true # use spot instances for cost savings

# Development Convenience
enable_deletion_protection = false # allow easy cleanup
skip_final_snapshot = true # faster teardown
publicly_accessible_db = true # enable remote management

# Default Credentials (change before production!)
default_db_username = "admin"
default_db_password = "admin123" # temporary default password

# API Keys for Testing
test_api_key = "sk_test_1234567890"
webhook_secret = "test_webhook_secret_123"