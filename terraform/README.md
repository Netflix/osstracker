# Deployment Example 

This directory contains a simple way to deploy the Netflix OSS Tracker on a single AWS EC2 server, utilizing Terraform. 

## 1. Requirements

1. Install Terraform: https://www.terraform.io/intro/getting-started/install.html
1. Setup Terraform for AWS access: https://www.terraform.io/docs/providers/aws/
1. Setup your own variables:

Create a file `terraform/aws/osstracker.auto.tfvars`, like so:  
```
aws_region = "eu-west-1"
availability_zone = "eu-west-1a"
vpc_cidr = "6.7.7.0/24"
key_name = "" # an EC2 key pair name. Please ensure this is in the same Region as the `aws_region` above.
private_key_path = "" # the path to the private key of the EC2 key pair above. 
```

## 2. Run

```bash
cd terraform/aws
terraform plan -var-file=osstracker.auto.tfvars .
# review the output to ensure terraform will do what you expect it to. 

# Then:
terraform apply -var-file=osstracker.auto.tfvars .
```

## 3. All set! Let's move to Ansible.

At this point Terraform will have created the inventory file that Ansible will need to run in: `$PROJECT_ROOT/ansible/production.ini`. 

Head on over to `$PROJECT_ROOT/ansible/README.md` for more instructions.