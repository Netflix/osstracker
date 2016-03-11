Overview
========

You can see more about OSS Tracker from our meetup [video](https://www.youtube.com/watch?v=5s-SS_aXoi0) and [slides](http://www.slideshare.net/aspyker/netflix-open-source-meetup-season-4-episode-1).

High Level View
===============

![High Level View](https://raw.githubusercontent.com/Netflix/osstracker/documentation/osstracker-docs/HighLevelView.png)

Manual installation
===================

Following manual installation is sample installation based on Linux system using docker containers. You can adapt it easily for other system using *docker-machine*, but care about `CASS_HOST` and `ES_HOST` they should be equals to `docker-machine ip MACHINE_NAME`.

**ATTENTION** neither *Cassandra* nor *ElasticSearch* containers define mounting volumes, if containers are removed data will be lost.

Clone OSS Tracker source code

```
git clone https://github.com/Netflix/osstracker.git
```

Run Cassandra instance (using docker)

```
# At start export CASS_HOST that should represent your docker ip
# linux only
export CASS_HOST=localhost
# If you're using docker-machine (Windows/Mac OSX) you should replace by following commented command
# MacOSX / Windows (with bash)
# export CASS_HOST=$(docker-machine ip <MACHINE NAME>)
export CASS_PORT=9042
docker run -d --name osstracker-cassandra -p ${CASS_PORT}:9042 cassandra
```

Init Cassandra DDL

```
docker run -it --link osstracker-cassandra:cassandra -v $(pwd)/osstracker-ddl/:/tmp/ddl/ --rm cassandra cqlsh cassandra -f /tmp/ddl/osstracker-simple.cql
```

Run ElasticSearch instance (using docker)

```
# At start export ES_HOST that should represent your docker ip
# linux only
export ES_HOST=localhost
# If you're using docker-machine (Windows/Mac OSX) you should replace by following commented command
# MacOSX / Windows (with bash)
# export ES_HOST=$(docker-machine ip <MACHINE NAME>)
export ES_PORT=9200
docker run -d --name osstracker-elasticsearch -p ${ES_PORT}:9200 elasticsearch
```

Init ElasticSearch DDL

```
curl -i -X PUT ${ES_HOST}:${ES_PORT}/osstracker
curl -i -X PUT -H "Content-Type: application/json" -d '{"properties":{"repo_name":{"type":"string","index":"not_analyzed"}}}' ${ES_HOST}:${ES_PORT}/osstracker/_mapping/repo_stats
```

Populate Cassandra data and ElasticSearch data (order is important Cassandra first then ElasticSearch)

```
export GITHUB_KEY=XXXXXX
./gradlew -PCASS_HOST=${CASS_HOST} -PCASS_PORT=${CASS_PORT} -PES_HOST=${ES_HOST} -PES_PORT=${ES_PORT} -PGITHUB_OAUTH=${GITHUB_KEY} -PACTION=updatecassandra clean run
./gradlew -PCASS_HOST=${CASS_HOST} -PCASS_PORT=${CASS_PORT} -PES_HOST=${ES_HOST} -PES_PORT=${ES_PORT} -PGITHUB_OAUTH=${GITHUB_KEY} -PACTION=updateelasticsearch run
```

Run Kibana instance (using docker)
**ATTENTION** code source hardcodes *Kibana* host and port so *Kibana* host must be equals to *ElasticSearch* host

```
docker run -d --name osstracker-kibana --link osstracker-elasticsearch:elasticsearch -p 5601:5601 kibana
```

Create Docker OSS Tracker console image

```
cd osstracker-console/
docker build -t netflixoss/osstracker-console .
```

Run OSS Tracker console (using docker)

```
docker run -d --name osstracker-console -e CASS_HOST=${CASS_HOST} -e CASS_PORT=${CASS_PORT} -e ES_HOST=${ES_HOST} -e ES_PORT=${ES_PORT} -p 3000:3000 netflixoss/osstracker-console
```

Enjoy


