#!/bin/bash
set -x

if [ -z "$BASEIMAGE" ] || [ -z "$REGBASE" ]; then
  echo need to define BASEIMAGE and REGBASE variables
  exit 1
fi

FINALIMAGE=$REGBASE/netflixoss/osstracker-scraper:latest
 
docker pull $BASEIMAGE
docker tag -f $BASEIMAGE javabase:latest
docker build -t netflixoss/osstracker-scraper:latest .
docker tag -f netflixoss/osstracker-scraper:latest $FINALIMAGE

RETRY_COUNT=5
build_succeeded=0
while [[ $RETRY_COUNT -gt 0 && $build_succeeded != 1 ]]; do
  docker push $FINALIMAGE
  if [ $? != 0 ]; then
    echo "push failed, will retry"
    RETRY_COUNT=$RETRY_COUNT-1
  else
    build_succeeded=1
  fi
done

if [[ $RETRY_COUNT -eq 0 ]]; then
  echo "all push retries failed, failing script"
  exit 1
fi
