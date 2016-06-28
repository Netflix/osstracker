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

import org.apache.http.client.methods._
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client._
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory
import play.api.libs.json.{Json, JsObject}


class ElasticSearchAccess(esHost: String, esPort: Int) {
  val logger = LoggerFactory.getLogger(getClass)

  def indexDocInES(url: String, jsonDoc: String): Boolean = {

    val client = HttpClientBuilder.create().build()
    val req = new HttpPost(getFullUrl(url))
    req.addHeader("Content-Type", "application/json")
    req.setEntity(new StringEntity(jsonDoc))

    val resp = client.execute(req)
    resp.getStatusLine.getStatusCode match {
      case 201 => {
        true
      }
      case _ => {
        logger.error(s"error creating es document for url = $url")
        val respS = EntityUtils.toString(resp.getEntity)
        logger.error(s"return code = ${resp.getStatusLine} and doc = ${respS}")
        false
      }
    }
  }

  def getESDocForRepo(simpleDate: String, repoName: String): Option[JsObject] = {
    val client = HttpClientBuilder.create().build()
    val req = new HttpPost(getFullUrl("/osstracker/repo_stats/_search"))
    req.addHeader("Content-Type", "application/json")
    val jsonDoc = raw"""{"query":{"bool":{"must":[{"match":{"repo_name":"$repoName"}},{"match":{"asOfYYYYMMDD":"$simpleDate"}}]}}}"""
    req.setEntity(new StringEntity(jsonDoc))

    val resp = client.execute(req)

    val resC = resp.getStatusLine.getStatusCode
    resp.getStatusLine.getStatusCode match {
      case 404 => None: Option[JsObject]
      case _ =>
        val respS = EntityUtils.toString(resp.getEntity)
        val jsVal = Json.parse(respS)
        val hits = (jsVal \ "hits" \ "total").as[Int]
        hits match {
          case 0 => None: Option[JsObject]
          case _ => Some(((jsVal \ "hits" \ "hits")(0) \ "_source").get.asInstanceOf[JsObject])
        }
    }
  }

  def getESDocForRepos(simpleDate: String): Option[JsObject] = {
    val client = HttpClientBuilder.create().build()
    val req = new HttpPost(getFullUrl("/osstracker/allrepos_stats/_search"))
    req.addHeader("Content-Type", "application/json")
    val jsonDoc = raw"""{"query":{"match":{"asOfYYYYMMDD":"$simpleDate"}}}"""
    req.setEntity(new StringEntity(jsonDoc))

    val resp = client.execute(req)

    val resC = resp.getStatusLine.getStatusCode
    resp.getStatusLine.getStatusCode match {
      case 404 => None: Option[JsObject]
      case _ =>
        val respS = EntityUtils.toString(resp.getEntity)
        val jsVal = Json.parse(respS)
        val hits = (jsVal \ "hits" \ "total").as[Int]
        hits match {
          case 0 => None: Option[JsObject]
          case _ => Some(((jsVal \ "hits" \ "hits")(0) \ "_source").get.asInstanceOf[JsObject])
        }
    }
  }

  def getFullUrl(uri: String): String = {
    s"http://${esHost}:${esPort}${uri}"
  }
}
