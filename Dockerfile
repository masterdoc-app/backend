FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/install/masterdoc-backend/lib ./lib
COPY build/install/masterdoc-backend/bin/masterdoc-backend ./bin/masterdoc-backend
EXPOSE 8081
ENV PORT=8081
ENTRYPOINT ["./bin/masterdoc-backend"]
