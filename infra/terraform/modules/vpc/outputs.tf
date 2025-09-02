output "public_subnet_id" {
  value = aws_subnet.public_subnet.id
}

output "my_vpc_id" {
  value = aws_vpc.my_vpc.id
}
