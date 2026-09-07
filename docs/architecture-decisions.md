# Architecture decisions

Status: the room-only meeting and workshop slices are implemented in the standalone Maven application and `frontend/`. The separately hosted browser prototype remains a UI reference with simulated state.

## Application stack

| Area | Decision | Reason |
| --- | --- | --- |
| Frontend | React + TypeScript + Vite | Familiar browser application tooling; no server rendering required |
| Backend | Java 21, Spring Boot, REST endpoints | Match the current Loomspan baseline and familiar Spring application structure |
| Persistence | Spring Data JPA with Hibernate | Demonstrate ordinary entities, repositories, and transactional services |
| Database | Embedded, file-backed H2 | No Docker or database installation; preserve business records across restarts |
| Initialization | Flyway SQL migrations | Initialize a new database automatically without reseeding every startup |
| Schema checking | Hibernate `ddl-auto=validate` | Flyway owns schema changes; Hibernate checks entity compatibility |
| Attachments | Local files | Keep the demo self-contained |
| Loomspan | Locally installed `0.1.0-SNAPSHOT` starter | Framework artifacts are not yet published to Maven |

Use the Spring Boot dependency management baseline compatible with the selected framework checkout. The inspected framework currently specifies Spring Boot 4.1.0 and Spring AI 2.0.0. Do not independently upgrade their managed Hibernate or related libraries without checking compatibility.

## Database lifecycle

Start with `V1__create_schema.sql` and `V2__seed_demo_data.sql`. Flyway applies versioned migrations on startup. Do not also initialize with `schema.sql`, `data.sql`, Hibernate schema creation, or a second seed runner.

The file-backed database lives in an ignored local data directory. Ordinary restarts retain assessments, bookings, and reservations. Provide an explicit reset command for the demo database and attachment fixtures; startup must never silently reset them.

Migration SQL is committed to source control. Database files, model credentials, runtime attachments, and logs are not. Add later schema changes through new migrations rather than modifying migrations already used by others.

Java skills call application services, which use repositories. Exact pricing, allocation checks, authorization, and booking transactions remain in those services. Test competing reservations against H2; do not assume Hibernate makes concurrency behavior identical across databases. PostgreSQL support is deferred rather than maintained as a second initial database profile.

## Independent repository

The completed application will have its own GitHub repository. It must not be a module in the Loomspan framework reactor, inherit the framework's development parent POM, or require an absolute path to the author's checkout.

Proposed source layout:

```text
README.md
pom.xml                 application Maven build
mvnw / mvnw.cmd / .mvn/  Maven wrapper
src/                    Spring application, skills, migrations, tests
frontend/               React + TypeScript + Vite application
docs/                   scope, scenarios, decisions, implementation stories
```

The existing `prototype/` is a separately hosted, simulated UI reference with its own Git metadata. Its Sites infrastructure and simulated business logic are not the production frontend architecture. Decide whether to retain it as a reference or archive it before preparing the final GitHub repository; do not accidentally embed its Git repository or publish its runtime/build artifacts.

During development, Vite can proxy API requests to Spring. For a packaged demo, aim to serve the compiled frontend from Spring so users can run a single application process. Node is a build/development dependency, not a required second production server.

## Local Loomspan dependency

Declare a `loomspan.version` Maven property set to `0.1.0-SNAPSHOT` and use it for:

```xml
<dependency>
    <groupId>ai.loomspan</groupId>
    <artifactId>loomspan-spring-boot-starter</artifactId>
    <version>${loomspan.version}</version>
</dependency>
```

Before building the demo, run this in the framework checkout:

```powershell
.\mvnw.cmd -pl loomspan-spring-boot-starter -am install
```

A full framework `mvn install` also works. Installation populates Maven's configured local repository, normally `.m2/repository`. Reinstall after framework changes and rebuild/restart the demo. The application uses normal Maven dependency resolution, not `systemPath`, copied JARs, or relative source imports.

Record the framework commit used for each tested demo milestone. A SNAPSHOT version alone does not identify an immutable implementation. Until artifacts are published, document the clone-and-install prerequisite for other developers; CI will also need an explicit framework checkout/install step at a selected commit. That is a build prerequisite, not a runtime dependency on a sibling directory.

## Next implementation milestone

Room-only and workshop assessment and booking are implemented. See [workshop-slice.md](workshop-slice.md) for the specialist contracts and migration/booking design. Requirement revisions and stored proposal comparison are implemented; see [revision-slice.md](revision-slice.md). Manager authorization is implemented through a restricted Java skill and simulated demo identities; see [manager-credit-slice.md](manager-credit-slice.md). Attachment intake remains later work.

The application has not yet been published to its own GitHub repository. The source layout above is now implemented; framework artifacts are resolved normally from the local Maven repository.
