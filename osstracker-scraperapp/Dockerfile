FROM openjdk:8-alpine

MAINTAINER NetflixOSS <netflixoss@netflix.com>

COPY build/libs/osstracker-scraperapp-*-all.jar /osstracker-scraperapp-all.jar

ENV github_oauth=1111111111111111111111111111111111111111
ENV github_org=Netflix
ENV github_login=yourloginhere

CMD ["java", "-jar", "/osstracker-scraperapp-all.jar", "--action", "updatecassandra"]
