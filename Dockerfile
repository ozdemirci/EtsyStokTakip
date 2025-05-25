FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY target/etsystoktakip-0.0.1-SNAPSHOT.jar app.jar

# Create logs directory and set permissions
RUN mkdir -p /app/logs && chmod 777 /app/logs

# Log dosyasını host ile paylaşmak için volume mount önerisi
VOLUME ["/app/logs"]

# Log kopyalama scriptini ekle
COPY copy-logs.sh /app/copy-logs.sh
RUN chmod +x /app/copy-logs.sh

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "/app/copy-logs.sh & exec java -jar app.jar"]
