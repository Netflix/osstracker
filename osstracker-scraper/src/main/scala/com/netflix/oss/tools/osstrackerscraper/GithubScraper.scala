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

import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.slf4j.LoggerFactory
import play.api.libs.json._
import org.joda.time.{DateTime, DateTimeZone}
import java.util.Date

import com.netflix.oss.tools.osstrackerscraper.OssLifecycle.OssLifecycle

import scala.collection.mutable.ListBuffer

class GithubScraper(githubOrg: String, cassHost: String, cassPort: Int, esHost: String, esPort: Int, reportWriter: ReportWriter) {
  def logger = LoggerFactory.getLogger(getClass)
  val now = new DateTime().withZone(DateTimeZone.UTC)
  val dtfISO8601 = ISODateTimeFormat.dateTimeNoMillis()
  val dtfSimple = DateTimeFormat.forPattern("yyyy-MM-dd")
  def asOfISO = dtfISO8601.print(now)
  def asOfYYYYMMDD = dtfSimple.print(now)

  def updateElasticSearch(): Boolean = {
    val es = new ElasticSearchAccess(esHost, esPort)
    val cass = new CassandraAccesss(cassHost, cassPort)
    val github = new GithubAccess(asOfYYYYMMDD, asOfISO, true)

    try {
      println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

      // get all the known repos from cassandra, sorted in case we run out github API calls
      val cassRepos = cass.getAllRepos()
      val cassReposNames = cassRepos.map(_.name).toSet
      logger.debug(s"repos(${cassReposNames.size}) in cass = $cassReposNames")

      // get all of the known repos from github
      val githubRepos = github.getAllRepositoriesForOrg(githubOrg)
      val githubReposNames = githubRepos.map(_.getName).toSet
      logger.debug(s"repos(${githubReposNames.size}) on GH = $githubReposNames")

      val commonRepoNames = cassReposNames.intersect(githubReposNames)
      val onlyInCassReposNames = cassReposNames.diff(githubReposNames)
      val onlyInGHReposNames = githubReposNames.diff(cassReposNames)

      logger.error(s"need to delete the following repos from cassandra - $onlyInCassReposNames")
      logger.info(s"new repos detected on github that aren't in cassandra - $onlyInGHReposNames")

      val commonReposCassRepos = commonRepoNames.map(name => cassRepos.find(name == _.name).get)
      val commonReposCassReposOrderByLastUpdate = collection.SortedSet[RepoInfo]()(ESDateOrdering) ++ commonReposCassRepos
      val commonReposCassReposOrderByLastUpdateNames = commonReposCassReposOrderByLastUpdate.toList.map(_.name)

      val orderToUpdate = commonReposCassReposOrderByLastUpdateNames ++ onlyInGHReposNames

      val docsList = new ListBuffer[JsObject]()

      // create or validate that ES document exists for each repo
      for (repoName <- orderToUpdate) {
        val ghRepo = githubRepos.find(_.getName == repoName).get
        val cassRepo = cassRepos.find(_.name == repoName)
        val (public, ossLifecycle) = cassRepo match {
          case Some(repo) => (repo.public, repo.osslifecycle)
          case _ => (false, OssLifecycle.Unknown)
        }

        val alreadyExistsDoc = es.getESDocForRepo(asOfYYYYMMDD, repoName)

        if (alreadyExistsDoc.isEmpty) {
          val stat = github.getRepoStats(ghRepo, public, ossLifecycle)
          var indexed = es.indexDocInES("/osstracker/repo_stats", stat.toString)
          if (!indexed) {
            return false
          } else {
            val releaseStats = github.getRepoDownloads(ghRepo, public, ossLifecycle)

            releaseStats.foreach( rel => {
              indexed &= es.indexDocInES("/osstracker/repo_downloads", rel.toString)
            })
            if (!indexed) {
              return false
            }

          }
          docsList += stat
        }
        else {
          logger.info(s"skipping up index of repo doc for ${repoName}, ${asOfYYYYMMDD}")
          docsList += alreadyExistsDoc.get
        }

        val success = cass.markReposLastUpdateDateES(repoName)
        if (!success) {
          return false
        }
      }

      val alreadyExists = !es.getESDocForRepos(asOfYYYYMMDD).isEmpty
      if (alreadyExists) {
        logger.info(s"skipping up index of all repos doc for ${asOfYYYYMMDD}")
      }
      else {
        val numRepos = docsList.size
        val forks: Int = (docsList(0) \ "forks").as[Int]
        val totalForks = docsList.map(obj => (obj \ "forks").as[Int]).sum
        val totalStars = docsList.map(obj => (obj \ "stars").as[Int]).sum
        val totalOpenIssues = docsList.map(obj => (obj \ "issues" \ "openCount").as[Int]).sum
        val totalClosedIssues = docsList.map(obj => (obj \ "issues" \ "closedCount").as[Int]).sum
        val totalOpenPRs = docsList.map(obj => (obj \ "pullRequests" \ "openCount").as[Int]).sum
        val totalClosedPRs = docsList.map(obj => (obj \ "pullRequests" \ "closedCount").as[Int]).sum

        val reposJsonDoc: JsObject = Json.obj(
          "asOfISO" -> asOfISO,
          "asOfYYYYMMDD" -> asOfYYYYMMDD,
          "avgForks" -> totalForks / numRepos,
          "avgStars" -> totalStars / numRepos,
          // "numContributors" -> contributorLogins.length, // TODO: Need to fold all of the repos together
          "issues" -> Json.obj(
            "avgOpenCount" -> totalOpenIssues / numRepos,
            "avgClosedCount" -> totalClosedIssues / numRepos,
            "totalOpenCount" -> totalOpenIssues,
            "totalClosedCount" -> totalClosedIssues
            // "avgTimeToCloseInDays" -> avgIssues // TODO: Need to compute average
          ),
          "pullRequests" -> Json.obj(
            "avgOpenCount" -> totalOpenPRs / numRepos,
            "avgClosedCount" -> totalClosedPRs / numRepos,
            "totalOpenCount" -> totalOpenPRs,
            "totalClosedCount" -> totalClosedPRs
            // "avgTimeToCloseInDays" -> avgPRs // TODO: Need to compute average
          ),
          "commits" -> Json.obj(
            // "daysSinceLastCommit" -> daysSinceLastCommit // TODO: Need to compute average
          ),
          "repos" -> docsList
        )
        logger.debug("allrepos info json = " + reposJsonDoc)
        val indexed = es.indexDocInES("/osstracker/allrepos_stats", reposJsonDoc.toString)
        if (!indexed) {
          return false
        }
      }

      println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)
    }
    finally {
      cass.close()
    }

    true
  }


  def updateCassandra(): Boolean = {
    val cass = new CassandraAccesss(cassHost, cassPort)
    val github = new GithubAccess(asOfYYYYMMDD, asOfISO, true)
    val report = StringBuilder.newBuilder

    report.append(s"OSSTracker Report for ${asOfYYYYMMDD}\n\n")

    try {
      println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

      // get all the known repos from cassandra, sorted in case we run out github API calls
      val cassRepos = cass.getAllRepos()
      val cassReposNames = cassRepos.map(_.name).toSet
      logger.debug(s"repos(${cassReposNames.size}) in cass = $cassReposNames")

      // get all of the known repos from github
      val githubRepos = github.getAllRepositoriesForOrg(githubOrg)
      val githubReposNames = githubRepos.map(_.getName).toSet
      logger.debug(s"repos(${githubReposNames.size}) on GH = $githubReposNames")

      val commonRepoNames = cassReposNames.intersect(githubReposNames)
      val onlyInCassReposNames = cassReposNames.diff(githubReposNames)
      val onlyInGHReposNames = githubReposNames.diff(cassReposNames)

      // add new repos to cassandra
      logger.debug(s"repos that should be added to cassandra = $onlyInGHReposNames")
      if (onlyInGHReposNames.size > 0) {
        report.append(s"Found the following new repositories:\n")
        report.append(s"**************************************************\n")
        for (repoName <- onlyInGHReposNames) {
          report.append(s"\t$repoName\n")
        }
        report.append("\n")
      }

      val reposToAdd = onlyInGHReposNames.map(repoName => {
        val githubRepo = githubRepos.find(ghRepo => ghRepo.getName == repoName).get
        val repoInfo = new RepoInfo(repoName, Conf.SENTINAL_DEV_LEAD_ID, Conf.SENTINAL_MGR_LEAD_ID,
          Conf.SENTINAL_ORG, new Date(0), new Date(0), !githubRepo.isPrivate, githubOrg, true, OssLifecycle.Unknown)
        val success = cass.newRepo(repoInfo)
        if (!success) {
          return false
        }
      })

      // see what repos we should mark as non-existant in cassandra
      logger.error(s"repos that should be deleted from the database = $onlyInCassReposNames")
      if (onlyInCassReposNames.size > 0) {
        report.append(s"These repos should be deleted from the DB:\n")
        report.append(s"**************************************************\n")
        for (repoName <- onlyInCassReposNames) {
          report.append(s"\t$repoName\n")
        }
        report.append("\n")
      }

      val success1 = cass.markReposAsNonExistant(onlyInCassReposNames.toList)
      if (!success1) {
        return false
      }


      val cassReposNow = cass.getAllRepos()
      logger.debug(s"cassReposNow = $cassReposNow")

      val wentPublic = ListBuffer[String]()
      val wentPrivate = ListBuffer[String]()

      // see what repos we should change public/private in cassandra
      for (repo <- cassReposNow) {
        val cassPublic = repo.public
        val githubRepo = githubRepos.find(_.getName == repo.name)
        githubRepo match {
          case Some(ghRepo) => {
            val ghPublic = !ghRepo.isPrivate
            if (cassPublic != ghPublic) {
              logger.info(s"updating repo ${repo.name} with public = $ghPublic")
              val success = cass.updateGHPublicForRepo(repo.name, ghPublic)
              if (!success) {
                return false
              }

              if (ghPublic) {
                wentPublic += ghRepo.getName
              }
              else {
                wentPrivate += ghRepo.getName
              }
            }
          }
          case _ => {
            logger.error(s"github no longer has the repo ${repo.name}")
          }
        }
      }

      if (wentPublic.size > 0) {
        report.append(s"These repos went public:\n")
        report.append(s"**************************************************\n")
        for (repoName <- wentPublic) {
          report.append(s"\t$repoName\n")
        }
        report.append("\n")
      }

      if (wentPrivate.size > 0) {
        report.append(s"These repos went private:\n")
        report.append(s"**************************************************\n")
        for (repoName <- wentPrivate) {
          report.append(s"\t$repoName\n")
        }
        report.append("\n")
      }

      val changedLifecycle = ListBuffer[(String, OssLifecycle, OssLifecycle)]()
      val unknownLifecycle = ListBuffer[String]()

      // see what repos have changed OSS Lifecycle
      for (repo <- cassReposNow) {
        val githubRepo = githubRepos.find(_.getName == repo.name)
        githubRepo match {
          case Some(ghRepo) => {
            val lifecycle = github.getOSSMetaDataOSSLifecycle(ghRepo)
            if (lifecycle == OssLifecycle.Unknown) {
              unknownLifecycle += ghRepo.getName
            }
            if (lifecycle != repo.osslifecycle) {
              logger.info(s"updating repo ${repo.name} lifecycle from ${repo.osslifecycle} to $lifecycle")
              val success = cass.updateLifecycleForRepo(repo.name, lifecycle)
              if (!success) {
                return false
              }
              changedLifecycle += ((ghRepo.getName, repo.osslifecycle, lifecycle))
            }
          }
          case _ => {
            logger.error(s"github no longer has the repo ${repo.name}")
          }
        }
      }

      if (unknownLifecycle.size > 0) {
        report.append(s"These repos do not have correct OSS Lifecycle files:\n")
        report.append(s"**************************************************\n")
        for (repoName <- unknownLifecycle) {
          report.append(s"\t$repoName\n")
        }
        report.append("\n")
      }

      if (changedLifecycle.size > 0) {
        report.append(s"These repos changed oss lifecycle:\n")
        report.append(s"**************************************************\n")
        for (change <- changedLifecycle) {
          report.append(s"\t${change._1} went from ${change._2} to ${change._3}\n")
        }
        report.append("\n")
      }

      // mark all of the repos as last updated now
      logger.info("updating all repos in cassandra for last updated")
      val success2 = cass.markReposLastUpdateDateDB(cassReposNow.map(_.name))
      if (!success2) {
        return false
      }

      println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

      reportWriter.processReport(report.toString)
    }
    finally {
      cass.close()
    }

    true
  }

}