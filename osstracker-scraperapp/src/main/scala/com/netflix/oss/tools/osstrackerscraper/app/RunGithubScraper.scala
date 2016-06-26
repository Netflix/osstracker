package com.netflix.oss.tools.osstrackerscraper.app

import com.netflix.oss.tools.osstrackerscraper.{Conf, GithubScraper}
import org.slf4j.LoggerFactory

object RunGithubScraper {
  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]) {
    val conf = new Conf(args)

    val action = conf.action()

    val cassHost = System.getenv("CASS_HOST")
    val cassPort = System.getenv("CASS_PORT").toInt

    val esHost = System.getenv("ES_HOST")
    val esPort = System.getenv("ES_PORT").toInt

    if (action == Conf.ACTION_UPDATE_CASSANDRA) {
      val scraper = new GithubScraper(Conf.GITHUB_ORG, cassHost, cassPort, esHost, esPort)
      val success = scraper.updateCassandra()
      if (!success) {
        System.exit(1)
      }
      logger.info(s"successfully updated the cassandra repo infos")
    }
    else if (action == Conf.ACTION_UPDATE_ELASTICSEARCH) {
      val scraper = new GithubScraper(Conf.GITHUB_ORG, cassHost, cassPort, esHost, esPort)
      val success = scraper.updateElasticSearch()
      if (!success) {
        System.exit(1)
      }
      logger.info(s"successfully updated the elastic search repo infos")
    }
    else {
      println("you must specify an action")
      System.exit(1)
    }
  }
}