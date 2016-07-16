/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.oss.tools.osstrackerscraper

import java.io.IOException
import java.util.{Date, Properties}

import com.netflix.oss.tools.osstrackerscraper.OssLifecycle.OssLifecycle
import org.kohsuke.github._
import org.slf4j.LoggerFactory
import play.api.libs.json.{Json, JsObject}
import scala.collection.JavaConversions._

case class CommitInfo(numCommits: Int, daysSinceLastCommit: Int, contributorLogins: List[String]) {}
case class IssuesInfo(val closedIssuesSize: Int, val openIssuesSize: Int, val avgIssues: Int) {}
case class PRsInfo(val closedPRsSize: Int, val avgPRs: Int) {}

class GithubAccess(val asOfYYYYMMDD: String, val asOfISO: String) {
  val logger = LoggerFactory.getLogger(getClass)
  val github: GitHub = GitHub.connect()

  def getOSSMetaDataOSSLifecycle(repo: GHRepository): OssLifecycle = {
    try {
      val content: GHContent = repo.getFileContent("OSSMETADATA", "master")
      val contentIs = content.read()
      val props = new Properties()
      props.load(contentIs)
      val osslc = props.getProperty("osslifecycle", "UNKNOWN")
      OssLifecycleParser.getOssLifecycle(osslc)
    }
    catch {
      case ioe: IOException  => {
        ioe.printStackTrace()
        OssLifecycle.Unknown
      }
    }
  }

  def getRepoStats(repo: GHRepository, public: Boolean, ossLifecycle: OssLifecycle) : JsObject = {
    logger.info(s"repo = ${repo.getName()}, forks = ${repo.getForks}, stars = ${repo.getWatchers}")

    val openPullRequests = repo.getPullRequests(GHIssueState.OPEN)
    logger.debug(s"  openIssues = ${repo.getOpenIssueCount()}, openPullRequests = ${openPullRequests.size()}")

    // Note that in this case, the github-api will crash on calls to listIssues with java.lang.Error
    // https://github.com/kohsuke/github-api/issues/65
    var neverPushed = getCloseEnoughForSameDates(repo.getCreatedAt, repo.getPushedAt)

    val (commitInfo: CommitInfo, issuesInfo: IssuesInfo, prsInfo: PRsInfo) = if (neverPushed) {
      logger.warn("repo has never been pushed, so providing fake zero counts for issues and pull requests")
      (CommitInfo(0, 0, List[String]()), IssuesInfo(0, 0, 0), PRsInfo(0, 0))
    } else {
      val commitInfo = getCommitInfo(repo)
      val issuesInfo = getIssuesStats(repo)
      val prsInfo = getClosedPullRequestsStats(repo)
      (commitInfo, issuesInfo, prsInfo)
    }

    val repoJson: JsObject = Json.obj(
      "asOfISO" -> asOfISO,
      "asOfYYYYMMDD" -> asOfYYYYMMDD,
      "repo_name" -> repo.getName(),
      "public" -> public,
      "osslifecycle" -> ossLifecycle,
      "forks" -> repo.getForks(),
      "stars" -> repo.getWatchers(),
      "numContributors" -> commitInfo.contributorLogins.size,
      "issues" -> Json.obj(
        "openCount" -> issuesInfo.openIssuesSize,
        "closedCount" -> issuesInfo.closedIssuesSize,
        "avgTimeToCloseInDays" -> issuesInfo.avgIssues
      ),
      "pullRequests" -> Json.obj(
        "openCount" -> openPullRequests.size(),
        "closedCount" -> prsInfo.closedPRsSize,
        "avgTimeToCloseInDays" -> prsInfo.avgPRs
      ),
      "commits" -> Json.obj(
        "daysSinceLastCommit" -> commitInfo.daysSinceLastCommit
      ),
      "contributors" -> commitInfo.contributorLogins
    )
    logger.debug("repo json = " + repoJson)
    repoJson
  }

  // TODO: Is there a faster way to only pull the last commit?
  def getCommitInfo(repo: GHRepository) : CommitInfo = {
    val commits = repo.listCommits().asList()
    // disabled tracking of days since last commit until this issue is fixed:
    // https://github.com/kohsuke/github-api/issues/286
    //val orderedCommits = commits.sortBy(_.getCommitShortInfo.getCommitter().getDate())
    //val lastCommitDate = orderedCommits(orderedCommits.length - 1).getCommitShortInfo().getCommitter().getDate()
    //logger.debug(s"commits, first = ${orderedCommits(0).getSHA1}, last = ${orderedCommits(orderedCommits.length - 1).getSHA1()}")
    //val daysSinceLastCommit = daysBetween(lastCommitDate, new Date())
    val daysSinceLastCommit = 0
    logger.debug(s"daysSinceLastCommit = ${daysSinceLastCommit}")

    val contributors = commits.filter { commit => Option(commit.getAuthor()).isDefined }
    val contributorLogins = contributors.map(contributor => contributor.getAuthor().getLogin()).distinct
    logger.debug(s"numContribitors = ${contributorLogins.length}, contributorEmails = ${contributorLogins}")
    CommitInfo(commits.length, daysSinceLastCommit, contributorLogins.toList)
  }

  def getClosedPullRequestsStats(repo: GHRepository) : PRsInfo = {
    val closedPRs = repo.getPullRequests(GHIssueState.CLOSED)
    val timeToClosePR = closedPRs.map(pr => {
      val opened = pr.getCreatedAt()
      val closed = pr.getClosedAt()
      val difference = daysBetween(opened, closed)
      difference
    })
    val sumPRs = timeToClosePR.sum
    val avgPRs = timeToClosePR.size match {
      case 0 => 0
      case _ => sumPRs / timeToClosePR.size
    }
    logger.debug(s"avg days to close ${closedPRs.size()} pull requests = ${avgPRs} days")
    PRsInfo(closedPRs.size, avgPRs)
  }

  def getIssuesStats(repo: GHRepository) : IssuesInfo = {
    val closedIssues = repo.getIssues(GHIssueState.CLOSED).filter(_.getPullRequest == null)
    val openIssues = repo.getIssues(GHIssueState.OPEN).filter(_.getPullRequest == null)
    val timeToCloseIssue = closedIssues.map(issue => {
      val opened = issue.getCreatedAt()
      val closed = issue.getClosedAt()
      val difference = daysBetween(opened, closed)
      difference
    })
    val sumIssues = timeToCloseIssue.sum
    val avgIssues = timeToCloseIssue.size match {
      case 0 => 0
      case _ => sumIssues / timeToCloseIssue.size
    }
    logger.debug(s"avg days to close ${closedIssues.size()} issues = ${avgIssues} days")
    IssuesInfo(closedIssues.size, openIssues.size, avgIssues)
  }

  def daysBetween(smaller: Date, bigger: Date): Int = {
    val diff = (bigger.getTime() - smaller.getTime()) / (1000 * 60 * 60 * 24)
    diff.toInt
  }

  def getRemainingHourlyRate(): Int = {
    github.getRateLimit.remaining
  }

  def getAllRepositoriesForOrg(githubOrg: String): List[GHRepository] = {
    val org = github.getOrganization(githubOrg)
    val githubRepos = org.listRepositories(100).asList().toList
    githubRepos
  }

  def getCloseEnoughForSameDates(d1: Date, d2: Date): Boolean = {
    val d1T = d1.getTime
    val d2T = d2.getTime
    val diff = Math.abs(d1T - d2T)
    return diff < 1000*60; // 60 seconds
  }
}