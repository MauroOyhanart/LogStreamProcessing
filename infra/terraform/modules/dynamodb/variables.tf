variable common_tags {
  type = map(string)
  default = {}
}
variable kcl_app_name {
  type = string
  description = "Lease table name"
}

variable region {
  type = string
}

variable account_id {
  type = string
}

variable stream_name {
  type = string
}
