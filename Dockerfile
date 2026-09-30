FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       latexmk \
       texlive-latex-base \
       texlive-latex-extra \
       texlive-fonts-recommended \
       poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/career-raft-java-0.1.0-SNAPSHOT.jar app.jar
COPY data ./data
COPY config ./config
COPY resume ./resume
COPY cover_letter ./cover_letter

ENV CAREER_RAFT_ROOT=/app

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
