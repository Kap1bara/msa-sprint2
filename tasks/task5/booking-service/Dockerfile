# ---- build stage ----
FROM golang:1.21-alpine AS builder

WORKDIR /src
COPY main.go ./

# Static binary (smaller runtime image)
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o booking-service main.go

# ---- runtime stage ----
FROM alpine:3.20

RUN apk add --no-cache ca-certificates curl

WORKDIR /app
COPY --from=builder /src/booking-service /app/booking-service

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=2s --retries=3 CMD curl -fsS http://localhost:8080/ping || exit 1

CMD ["/app/booking-service"]
