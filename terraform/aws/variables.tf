provider "aws" {
  region = "${var.aws_region}"
}

variable "aws_region" {
  description = "AWS region to launch servers."
  default     = "eu-west-1"
}
variable "availability_zone" {
  default = "eu-west-1a"
}
variable "vpc_cidr" {
  default = "6.7.7.0/24" # you're wondering, right? well... 677 -> OSS
}
variable "key_name" {
  description = "Desired name of AWS key pair"
}
variable "private_key_path" {
  description = <<DESCRIPTION
Path to the SSH public key to be used for authentication.
Ensure this keypair is added to your local SSH agent so provisioners can
connect.
Example: ~/.ssh/terraform.pub
DESCRIPTION
}

data "aws_ami" "ubuntu" {
  most_recent = true

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-xenial-16.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  owners = ["099720109477"] # Canonical
}

