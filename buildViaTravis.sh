#!/bin/bash
# This script will build the project.

# Evaluating a pull request
if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  ./gradlew build

# Building a code commit, but not a release tag
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  ./gradlew -Prelease.travisci=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" build snapshot

# Building a release tag
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  case "$TRAVIS_TAG" in
  *-rc\.*)
    ./gradlew -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" candidate
    ;;
  *)
    ./gradlew -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" final
    ;;
  esac

  if [[ $? -ne 0 ]]; then
    exit 1
  fi
  ./gradlew :osstracker-scraperapp:shadowJar
  cd osstracker-scraperapp
  docker build -t netflixoss/osstracker-scraper:$TRAVIS_TAG .
  docker images
  docker login -u=${dockerhubUsername} -p=${dockerhubPassword}
  docker push netflixoss/osstracker-scraper:$TRAVIS_TAG
  cd ..
  cd osstracker-console
  docker build -t netflixoss/osstracker-console:$TRAVIS_TAG .
  docker images
  docker login -u=${dockerhubUsername} -p=${dockerhubPassword}
  docker push netflixoss/osstracker-console:$TRAVIS_TAG
  cd ..

# No a valid build
else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  ./gradlew build
fi
