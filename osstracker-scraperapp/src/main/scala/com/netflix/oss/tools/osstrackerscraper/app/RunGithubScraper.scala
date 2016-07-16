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
      val scraper = new GithubScraper(Conf.GITHUB_ORG, cassHost, cassPort, esHost, esPort, ConsoleReportWriter)
      val success = scraper.updateCassandra()
      if (!success) {
        System.exit(1)
      }
      logger.info(s"successfully updated the cassandra repo infos")
    }
    else if (action == Conf.ACTION_UPDATE_ELASTICSEARCH) {
      val scraper = new GithubScraper(Conf.GITHUB_ORG, cassHost, cassPort, esHost, esPort, ConsoleReportWriter)
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