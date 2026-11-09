# 🩸 Blood Donor Directory

A web application that helps people find blood donors based on blood group and location. Donors can register and manage their availability, making it easier to find people who are ready to donate.

**Built for:** Race 2 Create 2026 – Java Hackathon (Problem 65)

**Tech Stack:** Java, JDBC, MySQL, HTML, CSS, JavaScript.

## Features
- Register and manage donor details.
- Search donors by name, blood group, city or phone number.
- Filter donors by blood group and location.
- Update donor information and availability.
- View available donor counts by blood group.
- Delete donor records.

## How to Run
**Requirements:** JDK 11+ and MySQL.

- Windows: Run `run.bat`
- Mac/Linux: Run `./run.sh`

Open `http://localhost:8080` in your browser.

The application automatically creates the database and required table on the first run.

## Project Flow

```text
Frontend → Java Web Server → JDBC → MySQL
```

