resource "aws_security_group" "osstracker_sg" {
  name        = "osstracker_sg"
  description = "Opens up access to Netflix OSS Tracker console"
  vpc_id      = "${aws_vpc.osstracker_vpc.id}"

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
resource "aws_security_group_rule" "allow_ssh" {
  type            = "ingress"
  from_port       = 22
  to_port         = 22
  protocol        = "tcp"
  cidr_blocks     = ["0.0.0.0/0"]

  security_group_id = "${aws_security_group.osstracker_sg.id}"
}
resource "aws_security_group_rule" "allow_console" {
  type            = "ingress"
  from_port       = 3000
  to_port         = 3000
  protocol        = "tcp"
  cidr_blocks     = ["0.0.0.0/0"]

  security_group_id = "${aws_security_group.osstracker_sg.id}"
}
resource "aws_security_group_rule" "allow_kibana" {
  type            = "ingress"
  from_port       = 5601
  to_port         = 5601
  protocol        = "tcp"
  cidr_blocks     = ["0.0.0.0/0"]

  security_group_id = "${aws_security_group.osstracker_sg.id}"
}

resource "aws_instance" "osstracker" {
  connection {
    user = "ubuntu"
    private_key = "${file(var.private_key_path)}"
  }
  associate_public_ip_address = true

  instance_type = "t2.small"

  ami = "${data.aws_ami.ubuntu.id}"

  key_name = "${var.key_name}" # assumes it exists

  # Our Security group to allow HTTP and SSH access
  vpc_security_group_ids = ["${aws_security_group.osstracker_sg.id}"]

  subnet_id = "${aws_subnet.osstracker_subnet.id}"

  tags {
    Name = "osstracker"
  }


  provisioner "remote-exec" {
    inline = [
      "sudo apt-get -y update",
    ]
  }
  provisioner "remote-exec" {
    script = "../files/wait_for_instance.sh"
  }

}

data "template_file" "ansible_inventory" {
    template = "${file("${path.module}/../templates/ansible_inventory.tpl")}"

    vars {
        connection_strings = "${join("\n",formatlist("%s ansible_ssh_host=%s ansible_ssh_user=ubuntu ansible_ssh_private_key_file=%s",aws_instance.osstracker.*.tags.Name, aws_instance.osstracker.*.public_ip, var.private_key_path))}"
        list_nodes = "${join("\n",aws_instance.osstracker.*.tags.Name)}"
    }
}

resource "null_resource" "inventories" {
  triggers {
    template = "${data.template_file.ansible_inventory.rendered}"
  }

  provisioner "local-exec" {
      command = "echo '${data.template_file.ansible_inventory.rendered}' > ../../ansible/production.ini"
  }

}