# Paytm Project - Spring Boot Service

A Spring Boot service providing user authentication, JWT token generation, and RESTful APIs with PostgreSQL database support.

## Prerequisites
- Java 21+
- PostgreSQL (running on port 5433 or configured via environment variables)

## Quick Start (Local Development)

Run the application using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

## API Endpoints

### Authentication
- `POST /api/v1/auth/register` - Register a new user
- `POST /api/v1/auth/token` - Generate an auth token
- `POST /api/v1/auth/login` - User login
- `GET /api/v1/auth/me` - Get current authenticated user details (Requires `Authorization: Bearer <token>`)

### Monitoring & Health
- `GET /api/v1/health` - Health check endpoint
- `GET /actuator/health` - Application health & readiness probes
- `GET /actuator/prometheus` - Prometheus metrics
