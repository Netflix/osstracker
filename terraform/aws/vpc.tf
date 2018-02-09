
# Create a VPC to launch our instances into
resource "aws_vpc" "osstracker_vpc" {
  cidr_block = "${var.vpc_cidr}"
}

# Create an internet gateway to give our subnet access to the outside world
resource "aws_internet_gateway" "default" {
  vpc_id = "${aws_vpc.osstracker_vpc.id}"
}
# Grant the VPC internet access on its main route table
resource "aws_route" "internet_access" {
  route_table_id         = "${aws_vpc.osstracker_vpc.main_route_table_id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.default.id}"
}

resource "aws_subnet" "osstracker_subnet" {
  vpc_id            = "${aws_vpc.osstracker_vpc.id}"
  availability_zone = "${var.availability_zone}"
  cidr_block        = "${cidrsubnet(aws_vpc.osstracker_vpc.cidr_block, 4, 1)}"
  map_public_ip_on_launch = true
}