CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java","-jar","app.jar"]
