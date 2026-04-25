# UI Flow

This version is built to feel simple.

The user should not need to remember school data or type the same details again and again.

## Admin Flow

```mermaid
flowchart TD
    A["Login"] --> B["Admin Home"]
    B --> C["Teachers"]
    B --> D["School Lists"]
    B --> E["Students"]
    B --> F["Schedule"]
    B --> G["Requests"]
    B --> H["Reports"]
```

### Admin Home

Big next-step actions:

- Add Teacher
- Add Students
- Set Schedule
- Review Requests
- View Reports

### Teachers

- type teacher name
- type teacher email
- click add
- resend/reset password when needed

### School Lists

Admin saves:

- sections
- subjects
- rooms

These lists are reused everywhere else.

### Students

- choose section first
- import students by pasted rows
- or add one student manually
- resend QR from the list

### Schedule

Everything is picked from dropdowns:

- teacher
- section
- subject
- room
- day
- time

### Requests

Two clear groups:

- schedule requests
- student removal requests

### Reports

- choose filters
- read summary first
- check records below

## Teacher Flow

```mermaid
flowchart TD
    A["Login"] --> B["Teacher Home"]
    B --> C["Attendance"]
    B --> D["My Class List"]
    B --> E["My Schedule"]
    B --> F["Reports"]
```

### Teacher Home

Big next-step actions:

- Start Attendance
- My Class List
- Ask for Schedule Change
- Reports

### Attendance

```mermaid
flowchart TD
    A["Open Attendance Page"] --> B{"Scheduled class now?"}
    B -- Yes --> C["Class opens automatically"]
    B -- No --> D["Teacher may start temporary class"]
    C --> E["Scan student QR"]
    D --> E
    E --> F{"QR failed?"}
    F -- No --> G["Attendance saved"]
    F -- Yes --> H["Click student button"]
    H --> G
```

Important UI rules:

- one main attendance path
- no student ID typing for backup attendance
- only current class students appear in the manual list
- auto refresh checks the class again while the page is open

### My Class List

- shows students from sections in the teacher schedule
- teacher can only ask admin to remove a student

### My Schedule

- shows saved schedule
- teacher picks a class
- teacher chooses new values from saved dropdowns
- teacher sends request to admin

### Reports

- read summary
- read records
- ask AI questions about the current report
