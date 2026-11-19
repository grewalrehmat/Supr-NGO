# PACT Java Mini Project

PACT is a UCS553 web application for coordinating CSR projects, NGO events and volunteers. Directors manage projects and event budgets, union heads assign volunteer tasks, and volunteers see their assignments and upcoming events. Companies can still explore approved CSR projects and track NGO milestones.

## Run locally

Install JDK 17 or newer and Maven. If PostgreSQL command line tools are installed, start the local persistent database and app from this folder:

```sh
./db-start.sh
./run.sh
```

Alternatively, start the PostgreSQL container with Docker Compose:

```sh
docker compose up -d db
./run.sh
```

Open http://127.0.0.1:8080. To use another port, run `./run.sh 8090` and open the corresponding address. Maven downloads the PostgreSQL JDBC driver on first build.

The native setup stores its data in `data/postgres`; Docker Compose stores it in the persistent `pact-postgres` volume. The app creates its tables on first launch. If `data/pact.properties` exists and the new database is empty, PACT imports its existing users, projects, events, tasks and donations once.

To connect to another PostgreSQL instance, set `PACT_DB_URL`, `PACT_DB_USER` and `PACT_DB_PASSWORD`. Defaults are `jdbc:postgresql://localhost:5432/pact`, `pact` and `pact-local`.

To enable a reviewer account on first launch, set both variables before running the app. Use a unique demo password that you do not use elsewhere.

```sh
PACT_REVIEWER_EMAIL=reviewer@example.test PACT_REVIEWER_PASSWORD='replace-this-demo-password' ./run.sh
```

On Windows PowerShell, start the database, then compile and run from the project folder:

```powershell
docker compose up -d db
New-Item -ItemType Directory -Force build | Out-Null
mvn -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=build/lib
javac --release 17 --add-modules jdk.httpserver -cp 'build/lib/*' -d build (Get-ChildItem src/in/pact/*.java).FullName
java --add-modules jdk.httpserver -cp 'build;build/lib/*' in.pact.Main
```

For reviewer access on Windows, set `$env:PACT_REVIEWER_EMAIL` and `$env:PACT_REVIEWER_PASSWORD` before running the Java command.

## Demo walkthrough

1. Open Projects. Search for theme **Water**, district **Patiala** and budget **500000**. The ₹4,00,000 sample project appears. The ₹7,00,000 sample project exceeds the limit.
2. Sign up as an NGO and submit a project from the dashboard. It appears as pending review.
3. Sign out and sign in as the reviewer. Approve the new project. It becomes available in the public catalogue.
4. Sign back in as the NGO and record a dated milestone with evidence notes. Sign up as a company to view milestone progress from its dashboard.

### Volunteer and event coordination

1. Create accounts for an **NGO director**, **union head** and one or more **volunteers**. Use the same NGO name for each account so the team is easy to identify.
2. Sign in as the director and create a blood donation, plantation or clothing donation event. Set its date and planned budget. For a measurable goal, add a progress label and target (for example, **Trees planted** and **10,000**).
3. Record event expenses from the event card. The director dashboard summarizes active projects, volunteers, event budgets and recorded spending.
4. Sign in as the union head, select the event and a volunteer, then assign a task with instructions.
5. Sign in as that volunteer to see assigned tasks, upcoming events and any ongoing events with their live progress bars.
6. The union head can set an event to ongoing and update its current progress. Event status and progress are visible on the public Events page and volunteer dashboard.

### Donations

Companies and individual donors can record contributions from the approved project cards or the Events page. Contributions appear in the raised totals; the director dashboard includes the combined total. This prototype records donations for tracking and does not process payments.

## Structure

| File | Responsibility |
| --- | --- |
| `src/in/pact/Main.java` | HTTP server, routing, form handling and login sessions |
| `src/in/pact/Store.java` | Domain records, credential hashing, matching, event/task management and PostgreSQL persistence |
| `src/in/pact/Views.java` | Server-rendered HTML and CSS |
| `run.sh` | Compile and start the application on macOS or Linux |
| `db-start.sh` / `compose.yaml` | Start a persistent local PostgreSQL database |
| `pom.xml` | PostgreSQL JDBC driver dependency |

This Java implementation uses the JDK HTTP server and PostgreSQL through JDBC. Accounts, projects, milestones, events, tasks and donations persist in relational tables across restarts. Login sessions are held in memory. A first-run import preserves existing demo data from `data/pact.properties`; new changes are stored in PostgreSQL. Seeded projects use demonstration data. The server binds only to the local machine.
