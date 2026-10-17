FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/install/siderurgica-inventory-notifier/ /app/
RUN chmod +x /app/bin/siderurgica-inventory-notifier
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/siderurgica-inventory-notifier"]
