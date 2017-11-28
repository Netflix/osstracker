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
}
