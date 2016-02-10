package com.netflix.oss.tools.osstrackerscraper

import java.io.IOException
import java.util.{Date, Properties}

import com.netflix.oss.tools.osstrackerscraper.OssLifecycle.OssLifecycle
import org.kohsuke.github._
import org.slf4j.LoggerFactory
import play.api.libs.json.{Json, JsObject}
import scala.collection.JavaConversions._

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
        OssLifecycle.Unknown
      }
    }
  }

  def getRepoStats(repo: GHRepository, cassRepo: RepoInfo) : JsObject = {
    logger.info(s"repo = ${repo.getName}, forks = ${repo.getForks}, stars = ${repo.getWatchers}")
    if (repo.getSize == 0) {
      logger.warn(s"empty repository ${repo.getName}")
      val repoJson: JsObject = Json.obj(
        "asOfISO" -> asOfISO,
        "asOfYYYYMMDD" -> asOfYYYYMMDD,
        "repo_name" -> repo.getName,
        "public" -> cassRepo.public,
        "osslifecycle" -> cassRepo.osslifecycle,
        "forks" -> repo.getForks(),
        "stars" -> repo.getWatchers(),
        "numContributors" -> 0,
        "issues" -> Json.obj(
          "openCount" -> 0,
          "closedCount" -> 0,
          "avgTimeToCloseInDays" -> 0
        ),
        "pullRequests" -> Json.obj(
          "openCount" -> 0,
          "closedCount" -> 0,
          "avgTimeToCloseInDays" -> 0
        ),
        "commits" -> Json.obj(
          "daysSinceLastCommit" -> 0
        ),
        "contributors" -> List[String]()
      )
      repoJson
    }
    else {
      val openPullRequests = repo.getPullRequests(GHIssueState.OPEN)
      logger.debug(s"  openIssues = ${repo.getOpenIssueCount()}, openPullRequests = ${openPullRequests.size()}")

      val (numCommits, daysSinceLastCommit, contributorLogins) = commitInfo(repo)
      val (closedIssuesSize, avgIssues) = getClosedIssuesStats(repo)
      val (closedPRsSize, avgPRs) = getClosedPullRequestsStats(repo)

      val repoJson: JsObject = Json.obj(
        "asOfISO" -> asOfISO,
        "asOfYYYYMMDD" -> asOfYYYYMMDD,
        "repo_name" -> repo.getName(),
        "public" -> cassRepo.public,
        "osslifecycle" -> cassRepo.osslifecycle,
        "forks" -> repo.getForks(),
        "stars" -> repo.getWatchers(),
        "numContributors" -> contributorLogins.length,
        "issues" -> Json.obj(
          "openCount" -> repo.getOpenIssueCount(),
          "closedCount" -> closedIssuesSize,
          "avgTimeToCloseInDays" -> avgIssues
        ),
        "pullRequests" -> Json.obj(
          "openCount" -> openPullRequests.size(),
          "closedCount" -> closedPRsSize,
          "avgTimeToCloseInDays" -> avgPRs
        ),
        "commits" -> Json.obj(
          "daysSinceLastCommit" -> daysSinceLastCommit
        ),
        "contributors" -> contributorLogins.toSeq
      )
      logger.debug("repo json = " + repoJson)
      repoJson
    }
  }

  // TODO: Is there a faster way to only pull the last commit?
  def commitInfo(repo: GHRepository) : (Int, Int, List[String]) = {
    val commits = repo.listCommits().asList()
    val orderedCommits = commits.sortBy(_.getCommitShortInfo.getCommitter().getDate())
    val lastCommitDate = orderedCommits(orderedCommits.length - 1).getCommitShortInfo().getCommitter().getDate()
    //logger.debug(s"commits, first = ${orderedCommits(0).getSHA1}, last = ${orderedCommits(orderedCommits.length - 1).getSHA1()}")
    val daysSinceLastCommit = daysBetween(lastCommitDate, new Date())
    logger.debug(s"daysSinceLastCommit = ${daysSinceLastCommit}")

    val contributors = commits.filter { commit => Option(commit.getAuthor()).isDefined }
    val contributorLogins = contributors.map(contributor => contributor.getAuthor().getLogin()).distinct
    logger.debug(s"numContribitors = ${contributorLogins.length}, contributorEmails = ${contributorLogins}")
    (commits.length, daysSinceLastCommit, contributorLogins.toList)
  }

  def getClosedPullRequestsStats(repo: GHRepository) : (Int, Int) = {
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
    (closedPRs.size, avgPRs)
  }

  def getClosedIssuesStats(repo: GHRepository) : (Int, Int) = {
    val closedIssues = repo.getIssues(GHIssueState.CLOSED)
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
    (closedIssues.size(), avgIssues)
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
}
