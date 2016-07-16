package com.netflix.oss.tools.osstrackerscraper.app

import com.netflix.oss.tools.osstrackerscraper.ReportWriter

object ConsoleReportWriter extends ReportWriter {
  def processReport(reportContent: String) = {
    print(reportContent)
  }
}
