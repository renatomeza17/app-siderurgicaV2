FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/install/siderurgica-ruc/ /app/
RUN chmod +x /app/bin/siderurgica-ruc
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/siderurgica-ruc"]
