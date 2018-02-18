package com.netflix.oss.tools.osstrackerscraper

import com.fasterxml.jackson.databind.introspect.VisibilityChecker.Std
import org.scalatest.FunSuite
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility._
import org.kohsuke.github.{GHIssue}

class GitHubAccessTest extends FunSuite {
  test("Should correctly count issues based on label") {
    // copied from org.kohsuke.github.Requestor
    //val github = GitHub.connect("fake", "fake")
    val mapper = new ObjectMapper
    mapper.setVisibilityChecker(new Std(NONE, NONE, NONE, NONE, ANY));
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val issuesJSON = scala.io.Source.fromResource("security_monkey-issues.json").mkString
    val issues = mapper.readValue(issuesJSON, classOf[Array[GHIssue]])

    val access = new GithubAccess("a", "a", false)

    val stats = access.getIssuesStats(new Array[GHIssue](0), issues)
    assert(stats.openCountReallyOpen == 23)

    val issuesJSON2 = scala.io.Source.fromResource("hollow-issues.json").mkString
    val issues2 = mapper.readValue(issuesJSON2, classOf[Array[GHIssue]])

    val stats2 = access.getIssuesStats(new Array[GHIssue](0), issues2)
    assert(stats2.openCountReallyOpen == 13)
  }
}
