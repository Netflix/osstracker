package com.netflix.oss.tools.osstrackerscraper

import java.lang
import java.util.Date

import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core._
import com.netflix.oss.tools.osstrackerscraper.OssLifecycle.OssLifecycle
import org.slf4j.LoggerFactory

class RepoInfo(val name: String, val devLeadId: String, val mgrLeadId: String, val org: String,
  val statsLastUpdate: Date, val public: Boolean, val githubOrg: String, val githubExists: Boolean, val osslifecycle: OssLifecycle) {

  override def toString(): String = s"RepoOwnership($githubOrg/$name, $devLeadId, $mgrLeadId, $org, $statsLastUpdate, $public, $githubExists, ${osslifecycle.toString}})";
}

class CassandraAccesss(cassHost: String, cassPort: Int) {
  val logger = LoggerFactory.getLogger(getClass)

  val SELECT_ALL_FROM_REPOS_OWNERSHIP = "SELECT * FROM repo_info"
  val INSERT_INTO_REPOS_OWNERSHIP = "INSERT INTO repo_info (gh_repo_name, dev_lead_empid, mgr_lead_empid, org_short, last_stats_update, gh_public, gh_org, gh_exists, osslifecycle) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
  val UPDATE_REPOS_INFO_SET_NOT_EXIST = "UPDATE repo_info SET gh_exists = FALSE WHERE gh_repo_name = ?"
  val UPDATE_REPOS_TO_CURRENT_TIME = "UPDATE repo_info SET last_stats_update = ? WHERE gh_repo_name = ?"
  val UPDATE_REPOS_INFO_SET_PUBLIC = "UPDATE repo_info SET gh_public = ? WHERE gh_repo_name = ?"
  val UPDATE_REPOS_INFO_SET_LIFECYCLE = "UPDATE repo_info SET osslifecycle = ? WHERE gh_repo_name = ?"


  var cluster: Cluster = Cluster.builder().addContactPoint(cassHost).withPort(cassPort).build()
  var session: Session = cluster.connect(Conf.OSSTRACKER_KEYSPACE)

  val selectAllFromReposOwnership = session.prepare(SELECT_ALL_FROM_REPOS_OWNERSHIP)
  val insertIntoReposOwnershipPS = session.prepare(INSERT_INTO_REPOS_OWNERSHIP)
  val updateReposInfoSetNotExist = session.prepare(UPDATE_REPOS_INFO_SET_NOT_EXIST)
  val updateReposToCurrentTime = session.prepare(UPDATE_REPOS_TO_CURRENT_TIME)
  val updateReposInfoSetPublic = session.prepare(UPDATE_REPOS_INFO_SET_PUBLIC)
  val updateReposInfoSetLifecycle = session.prepare(UPDATE_REPOS_INFO_SET_LIFECYCLE)

  def close(): Unit = {
    session.close()
    cluster.close()
  }

  def getAllRepos() : List[RepoInfo] = {
    try {
      val rs = session.execute(selectAllFromReposOwnership.bind())
      import scala.collection.JavaConversions._
      val allRepos = rs.all().map(row => {
        val repoName = row.getString("gh_repo_name")
        val dev_lead_empid = row.getString("dev_lead_empid")
        val mgr_lead_empid = row.getString("mgr_lead_empid")
        val org_short = row.getString("org_short") // TODO: Deal with timezones
        val last_stats_update = row.getTimestamp("last_stats_update")
        val public = row.getBool("gh_public")
        val repoOrg = row.getString("gh_org")
        val exists = row.getBool("gh_exists")
        val osslifecycle = row.getString("osslifecycle")
        val osslifecycleE = OssLifecycleParser.getOssLifecycle(osslifecycle)
        val repoOwnership = new RepoInfo(repoName, dev_lead_empid, mgr_lead_empid, org_short, last_stats_update, public, repoOrg, exists, osslifecycleE)
        repoOwnership
      })
      allRepos.toList
    }
    catch {
      case ex: DriverException => {
        logger.error("failed to query all repos", ex)
        List[RepoInfo]()
      }
    }
  }

  def newRepo(repo: RepoInfo) : Boolean = {
    try {
      val statement = new BoundStatement(insertIntoReposOwnershipPS)
      session.execute(statement.bind(
        repo.name,
        repo.devLeadId,
        repo.mgrLeadId,
        repo.org,
        repo.statsLastUpdate,
        new lang.Boolean(repo.public),
        repo.githubOrg,
        new lang.Boolean(repo.githubExists),
        repo.osslifecycle.toString
      ))
      true
    }
    catch {
      case ex: DriverException => {
        logger.error("failed to upsert repo", ex)
        false
      }
    }
  }

  def markReposAsNonExistant(repos: Seq[String]) : Boolean = {
    for (repo <- repos) {
      try {
        val statement = new BoundStatement(updateReposInfoSetNotExist)
        session.execute(statement.bind(
          repo
        ))
      }
      catch {
        case ex: DriverException => {
          logger.error("failed to upsert repo", ex)
          return false
        }
      }
    }
    true
  }

  def markReposLastUpdateDate(repos: Seq[String]) : Boolean = {
    val now = new Date()

    for (repo <- repos) {
      try {
        val statement = new BoundStatement(updateReposToCurrentTime)
        session.execute(statement.bind(
          now,
          repo
        ))
      }
      catch {
        case ex: DriverException => {
          logger.error("failed to upsert repo", ex)
          return false
        }
      }
    }
    true
  }

  def updateGHPublicForRepo(repo: String, public: Boolean) : Boolean = {
    try {
      val statement = new BoundStatement(updateReposInfoSetPublic)
      session.execute(statement.bind(
        new lang.Boolean(public),
        repo
      ))
    }
    catch {
      case ex: DriverException => {
        logger.error("failed to upsert repo", ex)
        return false
      }
    }
    true
  }

  def updateLifecycleForRepo(repo: String, ossLifecycle: OssLifecycle) : Boolean = {
    try {
      val statement = new BoundStatement(updateReposInfoSetLifecycle)
      session.execute(statement.bind(
        ossLifecycle.toString,
        repo
      ))
    }
    catch {
      case ex: DriverException => {
        logger.error("failed to upsert repo", ex)
        return false
      }
    }
    true
  }
}