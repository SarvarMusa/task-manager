# Task Manager API (Spring Boot)

A simple REST API for managing tasks (create, list, view, delete).  
Built with **Spring Boot** and **Java**.

## Features

- Create a task
- List all tasks
- Get task by id
- Delete task by id
- Uses UUID as task identifier

## Tech Stack

- Java (JDK 25)
- Spring Boot
- Spring Web (REST)
- Spring Data JPA
- (Database: configure in `application.yaml`)

## Project Structure (high level)

- `src/main/java` — application source code
  - `controller` — REST controllers
  - `service` — business logic
  - `repository` — persistence layer
  - `entity` — JPA entities
  - `dto` — request/response models
- `src/main/resources/application.yaml` — app configuration
- `api-request/` — IntelliJ HTTP Client request files (`*.http`)

## Requirements

- Java 25 installed
- Maven (or use the included Maven Wrapper `./mvnw`)

## How to Run

### 2) Build and run the jar
