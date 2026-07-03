# Task Manager API

## Description
Task Manager API is a robust RESTful backend service built for managing tasks efficiently. It provides a structured way to create, read, update, and delete tasks, utilizing UUIDs as unique identifiers. The project focuses on clean architecture, comprehensive logging, global exception handling, and secure data management, acting as a reliable backend for any task management frontend.

## Tech Stack
- **Framework:** Java 17, Spring Boot, Spring Web (REST)
- **Database:** PostgreSQL (with Docker containerization) & H2 (for local development/testing)
- **Data Access:** Spring Data JPA, Flyway (for migrations)
- **Documentation:** OpenAPI/Swagger (SpringDoc)
- **Authentication:** JWT Authentication
- **Infrastructure:** Docker, Docker Compose

## Setup Instructions

### Prerequisites
- JDK 17 (or newer)
- Maven
- Docker & Docker Compose

### Local Development Setup

1. **Clone the repository:**
```bash
git clone https://github.com/SarvarMusa/task-manager.git
cd task-manager
```

2. **Start the database:**
```bash
docker-compose up -d
```

3. **Run the application:**
```bash
./mvnw spring-boot:run
```

## Environment Variables
Create a `.env` file to manage secrets (do not commit this file). Example:
```env
DB_URL=jdbc:postgresql://localhost:5432/taskdb
DB_USERNAME=postgres
DB_PASSWORD=secretpassword
JWT_SECRET=your_jwt_super_secret_key_here
```

## Usage Examples
- **Creating a task:** Use the provided `.http` files in the `api-request/` folder via IntelliJ HTTP Client, or navigate to Swagger UI.
- **Viewing logs:** The application implements automated request/response logging using Aspect-Oriented Programming (AOP).

## API Endpoints
Comprehensive API documentation is available via Swagger UI. Common endpoints include:

| Endpoint | Method | Description |
|---|---|---|
| `/api/tasks` | GET | List all tasks |
| `/api/tasks/{id}` | GET | Get a specific task by UUID |
| `/api/tasks` | POST | Create a new task |
| `/api/tasks/{id}` | DELETE | Delete a task |

## Screenshots
*(Add your screenshots here)*
- Swagger UI Documentation
- Postman / HTTP Client execution examples
