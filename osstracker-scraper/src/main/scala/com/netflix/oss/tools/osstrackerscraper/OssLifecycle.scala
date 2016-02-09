package com.netflix.oss.tools.osstrackerscraper

import com.netflix.oss.tools.osstrackerscraper.OssLifecycle.OssLifecycle

object OssLifecycle extends Enumeration {
  type OssLifecycle = Value

  val Private = Value("private")
  val PrivateCollab = Value("privatecollab")
  val Active = Value("active")
  val Maintenance = Value("maintenance")
  val Archived = Value("archived")
  val Unknown = Value("UNKNOWN")
  val Invalid = Value("INVALID")
}

object OssLifecycleParser {
  def getOssLifecycle(value: String): OssLifecycle = {
    value match {
      case "private" => OssLifecycle.Private
      case "privatecollab" => OssLifecycle.PrivateCollab
      case "active" => OssLifecycle.Active
      case "maintenance" => OssLifecycle.Maintenance
      case "archived" => OssLifecycle.Archived
      case "UNKNOWN" => OssLifecycle.Unknown
      case _ => OssLifecycle.Invalid
    }
  }
}
