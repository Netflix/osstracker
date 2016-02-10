package com.netflix.oss.tools.osstrackerscraper

import org.rogach.scallop.ScallopConf

class Conf(args: Seq[String]) extends ScallopConf(args) {
  val action = opt[String](required = true)
}

object Conf {
  val ACTION_UPDATE_CASSANDRA = "updatecassandra"
  val ACTION_UPDATE_ELASTICSEARCH = "updateelasticsearch"
  val OSSTRACKER_KEYSPACE = "osstracker"
  val SENTINAL_DEV_LEAD_ID = "111111"; // Assign to valid emp id
  val SENTINAL_MGR_LEAD_ID = "222222"; // Assign to valid emp id
  val SENTINAL_ORG = "UNKNOWN"; // Assign to unknown org until edited in console
  val GITHUB_ORG = "Netflix"
}