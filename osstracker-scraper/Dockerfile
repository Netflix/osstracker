FROM javabase:latest

MAINTAINER NetflixOSS <netflixoss@netflix.com>

COPY build/libs/osstracker-scraper-*.jar /.

ENV github_oauth=1111111111111111111111111111111111111111
ENV github_login=yourloginhere

CMD ["java", "-jar", "/osstracker-scraper-1.0-all.jar", "--action", "updatecassandra"]
