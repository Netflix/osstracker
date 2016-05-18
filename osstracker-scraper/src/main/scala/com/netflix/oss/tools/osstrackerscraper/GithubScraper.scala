package com.netflix.oss.tools.osstrackerscraper

import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.kohsuke.github.GHRepository

import org.slf4j.LoggerFactory
import play.api.libs.json._
import org.joda.time.{DateTimeZone, DateTime}
import java.util.Date
import scala.collection.mutable.ListBuffer

class GithubScraper(githubOrg: String, cassHost: String, cassPort: Int, esHost: String, esPort: Int) {
  def logger = LoggerFactory.getLogger(getClass)
  val now = new DateTime().withZone(DateTimeZone.UTC)
  val dtfISO8601 = ISODateTimeFormat.dateTimeNoMillis()
  val dtfSimple = DateTimeFormat.forPattern("yyyy-MM-dd")
  def asOfISO = dtfISO8601.print(now)
  def asOfYYYYMMDD = dtfSimple.print(now)

  def updateElasticSearch(): Boolean = {
    val github = new GithubAccess(asOfYYYYMMDD, asOfISO)
    println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

    val cass = new CassandraAccesss(cassHost, cassPort)
    val es = new ElasticSearchAccess(esHost, esPort)

    // get all the known repos from cassandra, sorted in case we run out github API calls
    val cassRepos = cass.getAllRepos()
    logger.debug(s"cassRepos = $cassRepos")
    val cassReposToUpdate = cassRepos.sortBy(_.statsLastUpdate)

    // get all of the known repos from github
    val githubRepos = github.getAllRepositoriesForOrg(githubOrg)
    logger.debug(s"githubRepos = $githubRepos")

    val sortedGHRepos: Seq[GHRepository] = cassRepos.map(repo => {
      githubRepos.find(_.getName == repo.name).get
    })

    val docsList = new ListBuffer[JsObject]()
    // create or validate that ES document exists for each repo
    for (repo <- sortedGHRepos) {
      val cassRepo = cassRepos.find(_.name == repo.getName).get
      val alreadyExistsDoc = es.getESDocForRepo(asOfYYYYMMDD, repo.getName)

      if (alreadyExistsDoc.isEmpty) {
        val stat = github.getRepoStats(repo, cassRepo)
        val indexed = es.indexDocInES("/osstracker/repo_stats", stat.toString)
        if (!indexed) {
          return false
        }
        docsList += stat
      }
      else {
        logger.info(s"skipping up index of repo doc for ${repo.getName()}, ${asOfYYYYMMDD}")
        docsList += alreadyExistsDoc.get
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

    cass.close() // TODO: Move this to a finally block

    println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

    true
  }


  def updateCassandra(): Boolean = {
    val github = new GithubAccess(asOfYYYYMMDD, asOfISO)
    println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

    // get all the known repos from github
    val githubRepos = github.getAllRepositoriesForOrg(githubOrg)

    logger.debug(s"githubRepos = $githubRepos")
    val githubNames = githubRepos.map(repo => repo.getName)

    // get all the known repos from cassandra
    //val cass = injector.getInstance(classOf[CassandraAccess])
    val cass = new CassandraAccesss(cassHost, cassPort)

    val cassRepos = cass.getAllRepos()
    logger.debug(s"cassRepos = $cassRepos")
    val cassNames = cassRepos.map(repo => repo.name)

    // add new repos to cassandra
    val reposNotInCass = githubNames.filterNot(cassNames.toSet)
    logger.debug(s"repos that should be added to cassandra = $reposNotInCass")

    val reposToAdd = reposNotInCass.map(repoName => {
      val githubRepo = githubRepos.find(ghRepo => ghRepo.getName == repoName).get
      val repoInfo = new RepoInfo(repoName, Conf.SENTINAL_DEV_LEAD_ID, Conf.SENTINAL_MGR_LEAD_ID,
        Conf.SENTINAL_ORG, new Date(0), !githubRepo.isPrivate, githubOrg, true, OssLifecycle.Unknown)
      val success = cass.newRepo(repoInfo)
      if (!success) {
        return false
      }
    })

    // see what repos we should mark as non-existant in cassandra
    val reposNotInGH = cassNames.filterNot(githubNames.toSet)
    logger.error(s"repos that should be deleted from cassandra = $reposNotInCass")
    // TODO: if this list is empty, need to send an email about repos that have been deleted
    val success1 = cass.markReposAsNonExistant(reposNotInGH)
    if (!success1) {
      return false
    }

    val cassReposNow = cass.getAllRepos()
    logger.debug(s"cassReposNow = $cassReposNow")

    // see what repos we should change public/private in cassandra
    for (repo <- cassReposNow) {
      val cassPublic = repo.public
      val githubRepo = githubRepos.find(_.getName == repo.name).get
      val ghPublic = !githubRepo.isPrivate
      if (cassPublic != ghPublic) {
        logger.info(s"updating repo ${repo.name} with public = $ghPublic")
        val success = cass.updateGHPublicForRepo(repo.name, ghPublic)
        if (!success) {
          return false
        }
      }
    }

    // see what repos have changed OSS Lifecycle
    for (repo <- cassReposNow) {
      val githubRepo = githubRepos.find(_.getName == repo.name).get
      val lifecycle = github.getOSSMetaDataOSSLifecycle(githubRepo)
      if (lifecycle != repo.osslifecycle) {
        logger.info(s"updating repo ${repo.name} lifecycle from ${repo.osslifecycle} to $lifecycle")
        val success = cass.updateLifecycleForRepo(repo.name, lifecycle)
        if (!success) {
          return false
        }
      }
    }

    // mark all of the repos as last updated now
    logger.info("updating all repos in cassandra for last updated")
    val success2 = cass.markReposLastUpdateDate(cassReposNow.map(_.name))
    if (!success2) {
      return false
    }

    cass.close() // TODO: Move this to a finally block

    println(Console.RED + s"remaining calls ${github.getRemainingHourlyRate()}" + Console.RESET)

    true
  }

}