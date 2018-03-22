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

import com.netflix.oss.tools.osstrackerscraper
import com.netflix.oss.tools.osstrackerscraper.OssLifecycle.OssLifecycle
import org.kohsuke.github._
import org.slf4j.LoggerFactory
import play.api.libs.json._

import scala.collection.JavaConversions._

case class CommitInfo(numCommits: Int, daysSinceLastCommit: Int, contributorLogins: List[String]) {}
case class IssuesInfo(
   val closedCount: Int,
   val openCount: Int,
   val avgDayToClose: Int,
   val openCountWithNoLabels: Int,
   val openCountWithLabelBug: Int,
   val openCountWithLabelDuplicate: Int,
   val openCountWithLabelEnhancement: Int,
   val openCountWithLabelHelpWanted: Int,
   val openCountWithLabelInvalid: Int,
   val openCountWithLabelQuestion: Int,
   val openCountWithLabelWontfix: Int,
   val openCountTrulyOpen: Int
) {}
case class PRsInfo(val closedPRsSize: Int, val avgPRs: Int) {}
case class AssetInfo(assetName: String, downloadsCount: Long) {}
case class ReleaseInfo(releaseName: String, assets: JsArray){}

class GithubAccess(val asOfYYYYMMDD: String, val asOfISO: String, val connectToGithub: Boolean) {

  val logger = LoggerFactory.getLogger(getClass)
  val github: Option[GitHub] = if (connectToGithub) Some(GitHub.connect()) else None

  implicit val releaseInfoWrites: OWrites[ReleaseInfo] = Json.writes[ReleaseInfo]
  implicit val assetInfoWrites: OWrites[AssetInfo] = Json.writes[AssetInfo]

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
      (CommitInfo(0, 0, List[String]()), IssuesInfo(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), PRsInfo(0, 0))
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
        "openCount" -> issuesInfo.openCount,
        "openCountOnlyIssues" -> issuesInfo.openCountTrulyOpen,
        "closedCount" -> issuesInfo.closedCount,
        "avgTimeToCloseInDays" -> issuesInfo.avgDayToClose,
        "openCountByStandardTags" -> Json.obj(
          "bug" -> issuesInfo.openCountWithLabelBug,
          "helpWanted" -> issuesInfo.openCountWithLabelHelpWanted,
          "question" -> issuesInfo.openCountWithLabelQuestion,
          "duplicate" -> issuesInfo.openCountWithLabelDuplicate,
          "enhancement" -> issuesInfo.openCountWithLabelEnhancement,
          "invalid" -> issuesInfo.openCountWithLabelInvalid,
          "wontfix" -> issuesInfo.openCountWithLabelWontfix
        ),
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

  def getRepoDownloads(repo: GHRepository, public: Boolean, ossLifecycle: osstrackerscraper.OssLifecycle.Value): List[JsObject] = {
    logger.info(s"Getting downloads for repo = ${repo.getName()}")

    // Note that in this case, the github-api will crash on calls to listIssues with java.lang.Error
    // https://github.com/kohsuke/github-api/issues/65
    val neverPushed = getCloseEnoughForSameDates(repo.getCreatedAt, repo.getPushedAt)

    val (releasesInfo: List[JsObject]) = if (neverPushed) {
      logger.warn("repo has never been pushed, so providing fake zero counts for downloads")
      List[JsObject]()
    } else {
      val relStats = getReleaseStats(repo)
      relStats
    }
    releasesInfo
  }


  // TODO: Is there a faster way to only pull the last commit?
  def getCommitInfo(repo: GHRepository) : CommitInfo = {
    val commits = repo.listCommits().asList()
    val orderedCommits = commits.sortBy(_.getCommitShortInfo.getCommitDate())
    val lastCommitDate = orderedCommits(orderedCommits.length - 1).getCommitShortInfo().getCommitDate()
    logger.debug(s"commits, first = ${orderedCommits(0).getSHA1}, last = ${orderedCommits(orderedCommits.length - 1).getSHA1()}")
    val daysSinceLastCommit = daysBetween(lastCommitDate, new Date())
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

  def getIssuesStats(repo: GHRepository): IssuesInfo = {
    val closedIssues = repo.getIssues(GHIssueState.CLOSED).filter(_.getPullRequest == null).toArray
    val openIssues = repo.getIssues(GHIssueState.OPEN).filter(_.getPullRequest == null).toArray
    getIssuesStats(closedIssues, openIssues)
  }

  def getIssuesStats(closedIssues: Array[GHIssue], openIssues: Array[GHIssue]): IssuesInfo = {
    val (openCountNoLabels, openCountWithLabelBug, openCountWithLabelDuplicate,
      openCountWithLabelEnhancement, openCountWithLabelHelpWanted,
      openCountWithLabelInvalid, openCountWithLabelQuestion, openCountWithLabelWontfix,
      openCountTrulyOpen) = getIssuesLabelStats(openIssues)


    val timeToCloseIssue = closedIssues.map(issue => {
      val opened = issue.getCreatedAt()
      val closed = issue.getClosedAt()
      val difference = daysBetween(opened, closed)
      difference
    })
    val sumIssues = timeToCloseIssue.sum
    val avgDaysToCloseIssues = timeToCloseIssue.size match {
      case 0 => 0
      case _ => sumIssues / timeToCloseIssue.size
    }
    logger.debug(s"avg days to close ${closedIssues.length} issues = ${avgDaysToCloseIssues} days")

    IssuesInfo(closedIssues.size, openIssues.size, avgDaysToCloseIssues, openCountNoLabels, openCountWithLabelBug,
      openCountWithLabelDuplicate, openCountWithLabelEnhancement,
      openCountWithLabelHelpWanted, openCountWithLabelInvalid, openCountWithLabelQuestion, openCountWithLabelWontfix,
      openCountTrulyOpen)
  }

  def getIssuesLabelStats(openIssues: Array[GHIssue]): (Int, Int, Int, Int, Int, Int, Int, Int, Int) = {
    val openCountNoLabels = openIssues.count(issue => issue.getLabels.size() == 0)
    // standard labels that count
    val openCountWithLabelBug = countLabelForIssues(openIssues, "bug")
    val openCountWithLabelHelpWanted = countLabelForIssues(openIssues, "help wanted")
    val openCountWithLabelQuestion = countLabelForIssues(openIssues, "question")
    // standard labels that dont' count
    val openCountWithLabelDuplicate = countLabelForIssues(openIssues, "duplicate")
    val openCountWithLabelEnhancement = countLabelForIssues(openIssues, "enhancement")
    val openCountWithLabelInvalid = countLabelForIssues(openIssues, "invalid")
    val openCountWithLabelWontfix = countLabelForIssues(openIssues, "wontfix")
    val openCountTrulyOpen = countLabelsForTrueIssues(openIssues)
    (
      openCountNoLabels, openCountWithLabelBug, openCountWithLabelDuplicate,
      openCountWithLabelEnhancement, openCountWithLabelHelpWanted,
      openCountWithLabelInvalid, openCountWithLabelQuestion, openCountWithLabelWontfix,
      openCountTrulyOpen)
  }

  def countLabelsForTrueIssues(issues: Array[GHIssue]): Int = {
    // note that some issues will have bug and enhancement, we need to honor the worst case label (bug)
    // note that some issues will have bug and invalid, we don't want to double count
    // so, if no label, count it
    // for single labels
    //    if (bug || help wanted || question) count it
    //    if (duplicate || enhancement || invalid || wont fix) don't count it
    // for multiple labels
    //    if (bug || help wanted || question) count it
    //    if no standard github labels count it
    val count: Int = issues.count(issue => {
      val labels = issue.getLabels.toList

      val shouldCount = if (labels.size == 0) {
        true // no labels so counts
      } else {
        if (hasBugOrQuestionLabel(labels)) {
          true // has bug or question, so counts
        }
        else if (hasInvalidOrWontFix(labels)) {
          false // has invalid or wontfix, so doesn't count
        }
        else {
          val duplicate = hasLabelOfName(labels, "duplicate")
          val enhancement = hasLabelOfName(labels, "enhancement")
          val helpwanted = hasLabelOfName(labels, "helpwanted")
          // by this point bug and question and invalid and wontfix = false
          val computed = (duplicate, enhancement, helpwanted) match {
            case (false, false, false) => true // no labels except custom labels
            case (false, false, true) => true // help wanted and [custom labels]
            case (false, true, false) => false // enhancement and [custom labels]
            case (false, true, true) => false // enhancement and helpwanted and [custom labels]
            case (true, false, false) => true // duplicate and [custom labels]
            case (true, false, true) => true // duplicate and helpwanted and [custom labels]
            case (true, true, false) => false // duplicate and enhancement and [custom labels]
            case (true, true, true) => false // duplicate, enhancement, help wanted and [custom labels]
          }
          computed
        }
      }


//      val shouldCount = if (labels.size == 0) true else {
//        // TODO: this doesn't work for enhancement&&help wanted (counts it, but shouldn't)
//        val standardCounts = hasLabelOfName(labels, "bug") || hasLabelOfName(labels, "help wanted") || hasLabelOfName(labels, "question")
//        val helpWantedAndEnhancement = hasLabelOfName(labels, "help wanted") && hasLabelOfName(labels, "enhancement")
//        val doesNotHaveSomeStandardLabels = !hasSomeStandardGithubLabels(labels)
//        standardCounts || doesNotHaveSomeStandardLabels
//      }
      logger.debug(s"issue ${issue.getNumber} counts = ${shouldCount}, labels = ${labels.map{_.getName}}")
      shouldCount
    })
    count
  }

  // Issues with these labels ALWAYS count
  def hasBugOrQuestionLabel(labels: List[GHLabel]): Boolean = {
    // Future: Eventually we can let custom labels be configured per scraper or per project (OSSMETADATA)
    hasLabelOfName(labels, "bug") || hasLabelOfName(labels, "question")
  }

  // Issues with these labels will never count as long as not Bug or Question
  def hasInvalidOrWontFix(labels: List[GHLabel]): Boolean = {
    // Future: Eventually we can let custom labels be configured per scraper or per project (OSSMETADATA)
    hasLabelOfName(labels, "invalid") || hasLabelOfName(labels, "wontfix")
  }

//  def hasSomeStandardGithubLabels(labels: List[GHLabel]): Boolean = {
//    hasLabelOfName(labels, "bug") || hasLabelOfName(labels, "help wanted") || hasLabelOfName(labels, "question") ||
//      hasLabelOfName(labels, "duplicate") || hasLabelOfName(labels, "enhancement") || hasLabelOfName(labels, "invalid") || hasLabelOfName(labels, "wontfix")
//  }

  def hasLabelOfName(labels: List[GHLabel], name: String): Boolean = {
    !labels.find(_.getName == name).isEmpty
  }

  def countLabelForIssues(issues: Array[GHIssue], label: String): Int = {
    val openCountWithLabelBug: Int = issues.count(issue =>
      issue.getLabels.size() != 0 &&
        !issue.getLabels.find(_.getName == label).isEmpty
    )
    openCountWithLabelBug
  }

  def getReleaseStats(repo: GHRepository) : List[JsObject] = {
    val allReleases = repo.listReleases().asList()
    var releaseStatistics = List[JsObject]()

    var totalDownloadsToDate: Long = 0
    allReleases.foreach(release => {

      var assetsStatistics = List[AssetInfo]()
      val assets = release.getAssets
      assets.foreach(
         asset =>{
           assetsStatistics = AssetInfo(asset.getName, asset.getDownloadCount) :: assetsStatistics
           totalDownloadsToDate += asset.getDownloadCount
         }
       )

      val releaseDownloadsJson: JsObject = Json.obj(
        "asOfISO" -> asOfISO,
        "asOfYYYYMMDD" -> asOfYYYYMMDD,
        "repo_name" -> repo.getName(),
        "release_name" -> release.getName,
        "assets" -> assetsStatistics,
        "total_downloads_to_date" -> totalDownloadsToDate
      )
      logger.debug("repo downloads json = " + releaseDownloadsJson)


      releaseStatistics = releaseDownloadsJson :: releaseStatistics
    })


    releaseStatistics
  }

  def daysBetween(smaller: Date, bigger: Date): Int = {
    val diff = (bigger.getTime() - smaller.getTime()) / (1000 * 60 * 60 * 24)
    diff.toInt
  }

  def getRemainingHourlyRate(): Int = {
    github.get.getRateLimit.remaining
  }

  def getAllRepositoriesForOrg(githubOrg: String): List[GHRepository] = {
    val org = github.get.getOrganization(githubOrg)
    val githubRepos = org.listRepositories(100).asList().toList
    logger.info(s"Found ${githubRepos.size} total repos for ${githubOrg}")
    githubRepos
  }

  def getCloseEnoughForSameDates(d1: Date, d2: Date): Boolean = {
    val d1T = d1.getTime
    val d2T = d2.getTime
    val diff = Math.abs(d1T - d2T)
    return diff < 1000*60; // 60 seconds
  }
}