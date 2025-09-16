# Terraform Infrastructure for Maple Payments Hub

This directory contains Terraform configurations for deploying the Maple Payments Hub to AWS.

## Architecture Overview

The infrastructure includes:

- **VPC**: Isolated network with public and private subnets across 3 AZs
- **ECS Fargate**: Container orchestration for the application
- **Application Load Balancer**: Public-facing load balancer with health checks
- **RDS PostgreSQL**: Managed database with backups and monitoring
- **MSK (Managed Kafka)**: Event streaming platform
- **S3**: File storage for ISO20022 payment files
- **Secrets Manager**: Secure storage for database credentials
- **CloudWatch**: Logging and monitoring
- **KMS**: Encryption key management

## Prerequisites

1. **AWS CLI configured** with appropriate credentials
2. **Terraform** >= 1.0 installed
3. **Docker** for building application images
4. **Appropriate IAM permissions** for resource creation

## Deployment

### 1. Initialize Terraform

```bash
cd infra/terraform
terraform init
```

### 2. Create terraform.tfvars

```bash
# terraform.tfvars
environment = "dev"
aws_region  = "us-east-1"

# Customize as needed
fargate_cpu    = 1024
fargate_memory = 2048
app_count      = 2

rds_instance_class = "db.t3.small"
kafka_instance_type = "kafka.t3.small"
```

### 3. Plan the deployment

```bash
terraform plan -var-file="terraform.tfvars"
```

### 4. Apply the configuration

```bash
terraform apply -var-file="terraform.tfvars"
```

## Environment-Specific Configurations

### Development
```hcl
environment = "dev"
app_count = 1
rds_instance_class = "db.t3.micro"
kafka_instance_type = "kafka.t3.small"
enable_deletion_protection = false
```

### Staging
```hcl
environment = "staging"
app_count = 2
rds_instance_class = "db.t3.small"
kafka_instance_type = "kafka.m5.large"
enable_deletion_protection = false
```

### Production
```hcl
environment = "prod"
app_count = 3
rds_instance_class = "db.r5.large"
kafka_instance_type = "kafka.m5.xlarge"
enable_deletion_protection = true
enable_enhanced_monitoring = true
```

## Outputs

After deployment, Terraform will output:

- **Application URL**: Load balancer DNS name
- **Database endpoint**: RDS connection string
- **Kafka brokers**: MSK bootstrap servers
- **S3 bucket**: File storage bucket name

## Security Considerations

### Network Security
- Application runs in private subnets
- Database accessible only from application security group
- ALB in public subnets with restricted access

### Data Protection
- All data encrypted in transit and at rest
- Database credentials stored in Secrets Manager
- KMS keys for encryption management

### Access Control
- IAM roles with least privilege principles
- Security groups with minimal required access
- VPC Flow Logs enabled for network monitoring

## Monitoring and Logging

### CloudWatch
- Application logs: `/ecs/maple-payments-hub`
- Database logs: RDS enhanced monitoring
- Kafka logs: `/aws/msk/maple-payments-hub`

### Metrics
- ECS service metrics (CPU, memory, network)
- RDS performance metrics
- Kafka cluster metrics
- Custom application metrics

## Backup and Disaster Recovery

### Database Backups
- Automated daily backups with 7-day retention
- Point-in-time recovery enabled
- Cross-region backup replication (production)

### Application Recovery
- Multi-AZ deployment for high availability
- Auto Scaling for load management
- Blue-green deployment capability

## Cost Optimization

### Development
- Use Spot instances where appropriate
- Smaller instance sizes
- Shorter backup retention periods

### Production
- Reserved instances for predictable workloads
- S3 Intelligent Tiering for file storage
- RDS Reserved instances

## Troubleshooting

### Common Issues

1. **ECS Tasks failing to start**
   ```bash
   aws ecs describe-services --cluster maple-payments-hub-cluster --services maple-payments-hub-service
   aws logs get-log-events --log-group-name /ecs/maple-payments-hub
   ```

2. **Database connection issues**
   ```bash
   aws rds describe-db-instances --db-instance-identifier maple-payments-hub-db
   ```

3. **Kafka connectivity problems**
   ```bash
   aws kafka describe-cluster --cluster-arn <cluster-arn>
   ```

### Useful Commands

```bash
# View ECS service status
aws ecs describe-services --cluster maple-payments-hub-cluster --services maple-payments-hub-service

# Check application logs
aws logs tail /ecs/maple-payments-hub --follow

# View RDS status
aws rds describe-db-instances --db-instance-identifier maple-payments-hub-db

# List Kafka topics
aws kafka list-configuration-revisions --arn <cluster-arn>

# Check ALB health
aws elbv2 describe-target-health --target-group-arn <target-group-arn>
```

## Cleanup

To destroy the infrastructure:

```bash
terraform destroy -var-file="terraform.tfvars"
```

**Warning**: This will permanently delete all resources including databases and file storage.

## Support

For infrastructure support:
- Check CloudWatch logs for application issues
- Review Terraform state for resource configuration
- Use AWS Console for resource-specific troubleshooting

## Contributing

1. Make changes in feature branches
2. Test with `terraform plan`
3. Submit pull request for review
4. Apply changes after approval
