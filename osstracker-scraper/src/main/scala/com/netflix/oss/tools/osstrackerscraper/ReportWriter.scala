package com.netflix.oss.tools.osstrackerscraper

trait ReportWriter {
  def processReport(reportContent: String)
}