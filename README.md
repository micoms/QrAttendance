# QR Attend

QR Attend is a Java Swing attendance system for schools. It uses:

- `MariaDB / XAMPP` for the main data
- `Resend` for email
- `Gemini` for the teacher `Ask AI` chat
- `ZXing + webcam-capture` for QR code creation and scanning

This project is now `DB-first`, which means the normal app flow is:

`UI -> AppDataStore -> service classes -> database / external services`

## What The App Can Do

- Admin can sign in and manage teachers
- Admin can add students by section and assign them to teachers
- Admin can create schedules and review requests
- Teacher can take attendance with QR or without QR
- Teacher can ask for schedule changes
- Teacher can ask the admin to remove a student from the class list
- Teacher can use `Ask AI` inside dashboard, attendance, and reports

## Project Requirements

- Java 25
- NetBeans (recommended for this project)
- MariaDB / XAMPP
- Internet access for:
  - `Resend`
  - `Gemini`

## Setup

1. Create the database with:
   - [`database/qrattend_full_schema.sql`](database/qrattend_full_schema.sql)
2. If you already have an older database, also run:
   - [`database/qrattend_admin_student_sections_migration.sql`](database/qrattend_admin_student_sections_migration.sql)
   - [`database/qrattend_security_cleanup_migration.sql`](database/qrattend_security_cleanup_migration.sql)
3. Check your DB config in:
   - [`config/database.properties`](config/database.properties)
   - or start from [`config/database.properties.example`](config/database.properties.example)
4. Open the NetBeans project.
5. Run `Main.java`.

## Important Config Keys

Inside `config/database.properties`, make sure these are correct:

- `db.enabled`
- `db.url`
- `db.username`
- `db.password`
- `mail.enabled`
- `mail.provider`
- `mail.apiKey`
- `mail.fromEmail`
- `ai.enabled`
- `ai.provider`
- `ai.apiKey`

## Main Starting Point

- App entry: [`src/ppb/qrattend/main/Main.java`](src/ppb/qrattend/main/Main.java)
- Main shell after login: [`src/ppb/qrattend/app/AppShell.java`](src/ppb/qrattend/app/AppShell.java)
- UI data facade: [`src/ppb/qrattend/app/AppDataStore.java`](src/ppb/qrattend/app/AppDataStore.java)

## Documentation

- [System Overview](docs/system-overview.md)
- [UI Flow](docs/ui-flow.md)
- [Feature Processes](docs/feature-processes.md)
- [Database Flow](docs/database-flow.md)
- [Code Map](docs/code-map.md)

## Notes For Developers

- The UI now uses separate screen classes under `src/ppb/qrattend/app/`.
- `AppShell` is now mainly the shell layout and screen switcher.
- `AppDataStore` is now a simpler DB-first facade.
- The teacher AI chat flow is handled by:
  - [`src/ppb/qrattend/app/store/StoreTeacherAssistantSupport.java`](src/ppb/qrattend/app/store/StoreTeacherAssistantSupport.java)
- User-facing message cleanup is handled by:
  - [`src/ppb/qrattend/app/store/StoreMessages.java`](src/ppb/qrattend/app/store/StoreMessages.java)
