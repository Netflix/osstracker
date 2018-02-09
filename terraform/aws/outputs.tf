output "address" {
  value = "${aws_instance.osstracker.public_ip}"
}