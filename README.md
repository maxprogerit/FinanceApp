# Smart Finance Dashboard

Personal Finance and Investment Management Web Application

## Features
- Expense and Income Tracking
- Budget Planning System
- Financial Analytics Dashboard
- AI Financial Insights
- Financial Forecasting
- Investment Portfolio Tracker
- Multi-Currency Support
- Financial Goals Tracking
- Data Export and Reporting
- Notification and Alert System

## Technology Stack
- **Backend**: Java Spring Boot
- **Frontend**: HTML, CSS, JavaScript, Tailwind CSS, Chart.js
- **Database**: H2
- **AI/ML**: TensorFlow.js
- **APIs**: Stock Market API, Exchange Rate API

## Setup Instructions
1. Ensure Java 17+ is installed
2. Run `mvn clean install` to build the project
3. Run `mvn spring-boot:run` to start the application
4. Access the application at http://localhost:8080
5. Access H2 console at http://localhost:8080/h2-console

## Project Structure
```
FinanceApp/
├── src/
│   ├── main/
│   │   ├── java/com/smartfinance/dashboard/
│   │   │   ├── SmartFinanceDashboardApplication.java
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   ├── controller/
│   │   │   └── dto/
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   └── js/
│   │       ├── templates/
│   │       └── application.properties
│   └── test/
└── pom.xml
```
