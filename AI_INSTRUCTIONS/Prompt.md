# SurakshaSetu

## AR-Based Industrial Safety Training, Certification & Workforce Management Platform

---

## 1. Project Overview

**SurakshaSetu** is a mobile-first industrial safety training, certification, and workforce management platform designed for workers in mining, steel, mica processing, and other high-risk industrial environments.

The platform combines:

- Mobile AR-based safety training
- Practical safety assessments
- QR-verifiable digital certificates
- Offline-first functionality
- Hindi and Santali localization
- Worker skill and certification tracking
- Job and shift assignment
- Daily task management
- Attendance tracking
- Leave management
- Salary and payslip visibility
- Safety-compliance monitoring

The aim is not only to train workers but to connect their **training, certification, skills, and actual job responsibilities** within one integrated system.

---

# 2. Problem Statement

Jharkhand is one of India's most important mineral-producing states and has a large workforce employed across:

- Coal mines
- Steel plants
- Mica processing units
- Heavy manufacturing facilities
- Contract-based industrial operations

A major part of this workforce includes:

- Young workers entering industry for the first time
- Contract workers
- Migrant workers
- Workers with limited previous industrial exposure
- Workers who may not be comfortable with English-only training material

Industrial workplaces involve high-risk environments where even a small mistake can result in serious injury, equipment damage, production loss, or fatalities.

Traditional safety-training systems have several major limitations.

---

## 3. Problems With Existing Safety Training

### 3.1 Classroom-Based Training Has Low Practical Retention

Most industrial safety training is delivered through:

- Printed manuals
- Classroom lectures
- PowerPoint presentations
- Safety videos
- Periodic demonstrations

These methods often teach workers what they should do, but they do not allow workers to practice the procedure in a realistic environment.

For example, a worker may understand theoretically how a fire extinguisher works but may still hesitate during a real fire emergency.

The difference between:

> “Knowing the procedure”

and

> “Being able to perform the procedure under pressure”

is extremely important in industrial safety.

---

## 3.2 Live Safety Drills Are Expensive and Operationally Disruptive

Industrial facilities such as mines and steel plants often operate continuously.

Stopping operations to conduct frequent live drills can result in:

- Production loss
- Equipment downtime
- Worker scheduling issues
- Safety risks during simulations
- Increased training cost

Because of this, practical drills cannot always be conducted frequently for every worker.

As a result, workers may receive theoretical training but very little practical exposure.

---

## 3.3 VR-Based Training Is Not Accessible to Everyone

Virtual Reality safety simulators can provide realistic training, but they have major limitations.

They often require:

- Dedicated VR headsets
- High-performance computers
- Specialized training rooms
- Expensive hardware
- Maintenance and technical support

This makes VR difficult to deploy across:

- Small mines
- Remote industrial locations
- Contractor-heavy workforces
- Temporary training centers

SurakshaSetu instead uses **mobile Augmented Reality**, which can run on supported Android smartphones without requiring an external headset.

---

## 3.4 Workers May Be Assigned to Jobs Without Proper Training

One of the biggest gaps in traditional systems is that training systems and workforce-management systems are often separate.

Example:

A worker may be assigned to a confined-space maintenance job even though:

- The worker has never completed confined-space training
- Their certificate has expired
- Their assessment score was poor
- Their required PPE training is incomplete

This creates a dangerous gap between:

**Training records**

and

**Actual job assignments**

SurakshaSetu is designed to close this gap.

---

## 3.5 Physical Certificates Are Difficult to Verify

Traditional safety certificates may be:

- Printed on paper
- Stored in HR files
- Lost or damaged
- Difficult to verify at the worksite
- Expired without being noticed
- Difficult for supervisors to check instantly

A supervisor should be able to verify a worker's safety qualification within seconds.

SurakshaSetu provides **QR-based digital certificates** that can be checked from the mobile application or a verification page.

---

## 3.6 Language Is a Major Barrier

Safety instructions are often written in English or technical Hindi.

Many workers may understand procedures better when training is available in their preferred language.

The platform therefore supports:

- English
- Hindi
- Santali

Training modules can also include:

- Voice instructions
- Visual demonstrations
- AR instructions
- Simple text

This reduces dependence on long written manuals.

---

## 3.7 Internet Connectivity Cannot Be Assumed

Mining and industrial locations may have:

- Weak mobile connectivity
- No network inside certain work zones
- Limited internet availability
- Unreliable data connections

A safety-training system must therefore not depend entirely on continuous internet access.

SurakshaSetu follows an **offline-first architecture**.

Workers can access downloaded:

- Training modules
- AR assets
- Safety instructions
- Assessments
- Certificates
- Job information
- Emergency instructions

Data is synchronized automatically when connectivity becomes available.

---

## 3.8 Contract and Temporary Workers Often Receive Inconsistent Training

Industrial organizations frequently work with:

- Permanent workers
- Contractors
- Temporary workers
- Migrant workers

Companies may not always invest equally in training temporary workers.

This can create major skill differences between workers performing similar high-risk tasks.

A mobile training platform allows organizations to provide standardized training to every worker without requiring expensive dedicated infrastructure.

---

# 4. Proposed Solution

SurakshaSetu provides one unified platform that connects:

```text
Worker
   ↓
Safety Training
   ↓
AR Practical Training
   ↓
Assessment
   ↓
Certification
   ↓
Skill Profile
   ↓
Job Eligibility
   ↓
Job Assignment
   ↓
Daily Work
   ↓
Attendance / Task Completion
   ↓
Compliance Monitoring
```

The worker's safety qualification is therefore directly connected to the work they are allowed to perform.

---

# 5. AR-Based Safety Training

The mobile application uses **Google ARCore** to provide interactive safety simulations.

Instead of simply watching a safety video, workers actively interact with the environment through the phone camera.

---

## 5.1 Fire & Explosion Response

Example training flow:

```text
Start Training
      ↓
Camera Opens
      ↓
Environment Scanning
      ↓
AR Hazard Appears
      ↓
Worker Identifies Emergency Exit
      ↓
Worker Selects Correct Fire Extinguisher
      ↓
AR Extinguisher Simulation
      ↓
PASS Technique
      ↓
Evacuation Sequence
      ↓
Training Score
```

Workers can practice:

- Fire-hazard identification
- Correct extinguisher selection
- Emergency-exit identification
- PASS extinguisher technique
- Evacuation order
- Safe-distance awareness

---

## 5.2 Gas Leak & Confined Space Training

Workers can practice:

- Hazard-zone identification
- Gas-leak recognition
- PPE selection
- Entry authorization
- Buddy-system procedures
- Safe evacuation
- Emergency response

The AR experience can display virtual hazard zones and guide workers step-by-step.

---

## 5.3 Future Safety Modules

The same platform can support additional modules such as:

- Machinery safety
- Electrical hazards
- PPE handling
- Chemical exposure
- Confined-space safety
- Emergency evacuation
- Equipment handling
- First-aid procedures

---

# 6. Assessment Engine

Training completion alone should not automatically mean that a worker is qualified.

After each training module, the worker completes an assessment.

Assessment types can include:

- Multiple-choice questions
- Image-based questions
- Scenario-based questions
- Safety-sequence questions
- AR practical assessments

Example:

```text
Training Completed
       ↓
Assessment
       ↓
Score Calculation
       ↓
Pass?
    ↙       ↘
  Yes       No
   ↓         ↓
Certificate  Weak Topics
   ↓         ↓
Skill        Retraining
Updated      ↓
             Reassessment
```

The system records:

- Number of attempts
- Score
- Training duration
- Weak areas
- Pass/fail status
- Assessment completion date

---

# 7. QR-Based Digital Certification

Workers who successfully complete the required training and assessment receive a digital certificate.

Each certificate contains:

- Unique certificate ID
- Worker ID
- Training module
- Issue date
- Expiry date
- Certificate status
- QR code

Verification flow:

```text
Supervisor Scans QR
        ↓
Certificate ID
        ↓
Backend Verification API
        ↓
Check Worker
        ↓
Check Certificate
        ↓
Check Expiry
        ↓
Check Revocation
        ↓
VALID / EXPIRED / REVOKED / INVALID
```

This allows instant verification at the worksite.

---

# 8. Unique Solution — Training-to-Employment & Workforce Management

The major extension added by SurakshaSetu is a **complete workforce and job-management system integrated directly with safety training**.

Most platforms stop at training and certification.

SurakshaSetu continues the process into actual employment and daily work.

---

## 8.1 Training-to-Job Eligibility Engine

Every industrial job can define:

- Required skills
- Required safety training
- Required certificates
- Certificate validity
- Experience requirements
- Required PPE
- Worksite requirements

Before assigning a worker, the system checks eligibility.

Example:

```text
New Job Assignment
        ↓
Check Worker Skills
        ↓
Check Required Training
        ↓
Check Certificate Validity
        ↓
Check Worker Availability
        ↓
Eligible?
     ↙       ↘
   Yes       No
    ↓         ↓
Assign Job   Identify Missing Requirement
              ↓
        Assign Required Training
              ↓
          Assessment
              ↓
         Certification
              ↓
        Worker Becomes Eligible
```

### Example

Job:

**Confined Space Maintenance**

Required:

- Confined Space Training
- Gas Hazard Training
- PPE Certification

Worker status:

- PPE Certification → Valid
- Gas Training → Valid
- Confined Space Certificate → Missing

Result:

```text
JOB ASSIGNMENT BLOCKED
```

The application automatically recommends:

```text
Complete Confined Space Safety Training
```

After successful training and assessment:

```text
Certificate Generated
        ↓
Skill Profile Updated
        ↓
Worker Eligible
        ↓
Job Assignment Allowed
```

This creates a direct link between **safety compliance and workforce operations**.

---

# 9. Job Management

Workers can view:

- Current job
- Worksite
- Department
- Supervisor
- Shift timing
- Required PPE
- Required certificates
- Assigned daily tasks
- Job status

Job statuses can include:

- Assigned
- In Progress
- Completed
- Blocked
- Awaiting Training

---

# 10. Daily Activity Management

Workers can view their daily work plan.

Example:

```text
Today's Shift

08:00 AM – Check In

Tasks:
1. Inspect ventilation unit
2. Check gas detector
3. Complete machine safety checklist
4. Submit inspection evidence

04:00 PM – End Shift
```

Supervisors can monitor:

- Task completion
- Worker progress
- Pending jobs
- Missed tasks
- Safety requirements

---

# 11. Attendance Management

The platform can support:

- Shift check-in
- Shift check-out
- QR-based attendance
- Site-based attendance
- Offline attendance
- Working-hour calculation
- Overtime tracking
- Monthly attendance history

Offline attendance records are stored locally and synchronized when internet access returns.

---

# 12. Leave Management

Workers can:

- View leave balance
- Apply for leave
- Select leave type
- Select leave dates
- Enter reason
- Track approval status

Supervisors/admins can:

- Approve leave
- Reject leave
- View leave history
- Monitor workforce availability

---

# 13. Salary & Payslip Management

Workers can view:

- Monthly salary
- Base salary
- Overtime
- Incentives
- Deductions
- Net pay
- Payment status
- Payslip

For large organizations, SurakshaSetu can integrate with an existing ERP/payroll system rather than replacing the entire payroll engine.

---

# 14. Safety Compliance Engine

The compliance engine continuously evaluates worker readiness.

Inputs:

- Training completion
- Assessment scores
- Certificate validity
- Certificate expiry
- Job requirements
- Worker skills
- Incident history
- Site assignment

Outputs:

- Worker compliance status
- Site compliance percentage
- Expiring certificates
- Training pending
- Non-compliant workers
- High-risk assignments
- Required retraining

Example:

```text
Worker Assigned to High-Risk Job
           ↓
Compliance Check
           ↓
Required Certificate Valid?
       ↙              ↘
     Yes              No
      ↓                ↓
Assignment Allowed   Assignment Blocked
                       ↓
                 Training Assigned
```

---

# 15. Emergency SOS

Workers can trigger an emergency alert from the application.

Emergency categories:

- Fire
- Gas leak
- Worker injury
- Machinery accident
- Other emergency

Possible alert data:

- Worker ID
- Worksite
- Timestamp
- Emergency type
- Last available location
- Optional message

The system can notify:

- Supervisor
- Safety officer
- Site control room
- Admin dashboard

Emergency safety instructions remain available offline.

---

# 16. Offline-First Architecture

Industrial environments cannot rely on continuous internet connectivity.

The mobile app therefore stores essential information locally.

Offline content includes:

- Worker profile
- Assigned jobs
- Daily tasks
- Training modules
- AR assets
- Assessments
- Certificates
- Emergency instructions
- Attendance records
- Pending updates

Flow:

```text
Worker Action
     ↓
Local Room Database
     ↓
Sync Queue
     ↓
Internet Available?
   ↙             ↘
  No             Yes
  ↓               ↓
Store Locally   Sync With Backend
                  ↓
             PostgreSQL
                  ↓
            Updated Response
                  ↓
             Local Database
```

---

# 17. Localization

Supported languages:

- English
- Hindi
- Santali

Training content can contain:

- Text
- Audio
- Images
- AR guidance
- Short videos

The purpose is to reduce dependency on technical English and long text-heavy manuals.

---

# 18. User Roles

## Worker

Can access:

- Training
- Assessments
- Certificates
- Jobs
- Tasks
- Attendance
- Leave
- Salary
- Emergency SOS
- Profile

---

## Supervisor

Can access:

- Worker list
- Job assignment
- Task assignment
- Attendance
- Safety eligibility
- Worker certificates
- Task verification

---

## Safety Officer / Trainer

Can access:

- Training assignment
- Assessment reports
- Training completion
- Worker safety status
- Certificate monitoring
- Compliance alerts

---

## Admin

Can manage:

- Organizations
- Sites
- Workers
- Jobs
- Training
- Assessments
- Certificates
- Attendance
- Leave
- Payroll information
- Compliance
- Analytics
- Emergencies

---

# 19. Technology Stack

## Mobile Application

### Android

**Kotlin**

Why:

- Native Android performance
- Strong Android ecosystem
- Better control over camera and AR features
- Reliable background tasks
- Suitable for Android 10+ devices

---

## UI

**Jetpack Compose**

Used for:

- Mobile UI
- Navigation
- Reusable components
- Responsive layouts
- State-driven UI

---

## Augmented Reality

**Google ARCore**

Used for:

- Environment tracking
- Plane detection
- AR object placement
- Camera-based interaction
- Industrial AR training scenarios

Supporting options:

- SceneView
- Filament
- GLTF / GLB 3D models

---

## Local Storage

**Room Database**

Used for:

- Offline worker data
- Training progress
- Jobs
- Attendance
- Assessments
- Pending sync operations

---

## Background Synchronization

**Android WorkManager**

Used for:

- Offline-to-online synchronization
- Uploading pending actions
- Downloading updated training content
- Periodic data refresh

---

# 20. Backend

## Runtime

**Node.js**

---

## Backend Framework

**Express.js**

Express.js will power the REST API used by:

- Android mobile application
- Admin dashboard
- Certificate verification page

Backend responsibilities include:

- Authentication
- Worker management
- Training management
- Assessment processing
- Certificate generation
- Job assignment
- Attendance
- Leave
- Payroll information
- Compliance
- Notifications
- Emergency alerts
- Analytics

---

## Suggested Express.js Project Structure

```text
backend/
│
├── src/
│   ├── config/
│   │
│   ├── controllers/
│   │   ├── auth.controller.js
│   │   ├── worker.controller.js
│   │   ├── training.controller.js
│   │   ├── assessment.controller.js
│   │   ├── certificate.controller.js
│   │   ├── job.controller.js
│   │   ├── attendance.controller.js
│   │   ├── leave.controller.js
│   │   ├── payroll.controller.js
│   │   └── emergency.controller.js
│   │
│   ├── routes/
│   │
│   ├── services/
│   │
│   ├── middleware/
│   │
│   ├── validators/
│   │
│   ├── utils/
│   │
│   ├── jobs/
│   │
│   └── app.js
│
├── prisma/
│   └── schema.prisma
│
├── server.js
└── package.json
```

---

# 21. Database

## PostgreSQL

PostgreSQL is used as the primary database.

It is a good fit because the system contains many relational entities.

Example relationships:

```text
Organization
   ↓
Sites
   ↓
Workers
   ↓
Jobs
   ↓
Shifts
```

and:

```text
Worker
 ├── Training Progress
 ├── Assessments
 ├── Certificates
 ├── Job Assignments
 ├── Attendance
 ├── Leave
 └── Payroll Records
```

---

## Database ORM

Recommended:

**Prisma ORM**

Benefits:

- Easy PostgreSQL integration
- Clear relational schema
- Type-safe queries
- Database migrations
- Cleaner backend development

---

# 22. Major Database Entities

```text
User
Role
Organization
Site
Department
Worker

TrainingModule
TrainingProgress
ARScenario

Assessment
Question
AssessmentAttempt

Certificate
CertificateVerification

Job
JobRequirement
JobAssignment

Shift
Task
TaskAssignment

Attendance

LeaveRequest

PayrollRecord

EmergencyAlert
Incident

Notification

ComplianceRecord

AuditLog
```

---

# 23. Authentication

Recommended authentication:

```text
Mobile Number / Employee ID
           ↓
Authentication API
           ↓
OTP Verification
           ↓
Access Token
           ↓
Refresh Token
```

Use:

- JWT access token
- Refresh token
- Role-based access control

Roles:

```text
WORKER
SUPERVISOR
TRAINER
SAFETY_OFFICER
HR
SITE_ADMIN
ORG_ADMIN
SUPER_ADMIN
```

---

# 24. File & AR Asset Storage

Use object storage for:

- 3D models
- Training videos
- Audio
- Images
- Certificates
- Payslips
- Documents

Recommended services:

- AWS S3
- Cloudflare R2
- Supabase Storage

AR assets can be downloaded to the Android device for offline usage.

---

# 25. Notifications

Recommended:

**Firebase Cloud Messaging**

Notifications can include:

- Training assigned
- Training due
- Certificate generated
- Certificate expiring
- New job assigned
- Shift reminder
- Leave approved
- Leave rejected
- Payslip available
- Emergency alert

---

# 26. Admin Dashboard

Recommended frontend:

**React.js**

Possible supporting tools:

- React Router
- Axios
- TanStack Query
- Recharts
- Tailwind CSS or normal CSS component system

Admin dashboard modules:

```text
Dashboard
Workers
Training
Assessments
Certificates
Jobs
Shifts
Tasks
Attendance
Leave
Payroll
Compliance
Emergency
Analytics
Settings
```

---

# 27. Recommended Full Tech Stack

| Layer | Technology |
|---|---|
| Mobile App | Kotlin |
| Android UI | Jetpack Compose |
| AR | Google ARCore |
| AR Rendering | SceneView / Filament |
| Local Database | Room |
| Background Sync | WorkManager |
| Backend | Node.js |
| API Framework | Express.js |
| Main Database | PostgreSQL |
| ORM | Prisma |
| Authentication | JWT + OTP |
| Admin Dashboard | React.js |
| API Calls | Axios |
| Push Notifications | Firebase Cloud Messaging |
| Object Storage | AWS S3 / Cloudflare R2 |
| Cache | Redis |
| Background Jobs | BullMQ |
| Deployment | Docker |
| Backend Hosting | Render / Railway / AWS |
| Frontend Hosting | Vercel / Render |
| CI/CD | GitHub Actions |
| Version Control | Git + GitHub |

---

# 28. High-Level Architecture

```text
                    ┌─────────────────────┐
                    │       WORKER        │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   ANDROID APP       │
                    │ Kotlin + Compose    │
                    └──────────┬──────────┘
                               │
               ┌───────────────┼────────────────┐
               │               │                │
               ▼               ▼                ▼
          AR Training      Workforce         Offline
            ARCore         Management        Room DB
               │               │                │
               └───────────────┼────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   Express.js API    │
                    │      Node.js        │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
   PostgreSQL                Redis               Object
   Database                                      Storage
        │
        ▼
 Training / Jobs / Attendance / Certificates /
 Assessment / Leave / Payroll / Compliance
                               │
                               ▼
                    ┌─────────────────────┐
                    │ React Admin Panel   │
                    └─────────────────────┘
```

---

# 29. Main Innovation

The primary innovation of SurakshaSetu is not simply mobile AR training.

The key innovation is:

> **A worker's training, assessment, certification, skill profile, and actual job eligibility are connected inside one platform.**

Traditional flow:

```text
Training System
     ↓
Certificate

HR System
     ↓
Job Assignment
```

These systems may not communicate properly.

SurakshaSetu:

```text
AR Training
     ↓
Assessment
     ↓
Certification
     ↓
Worker Skill Profile
     ↓
Safety Compliance Check
     ↓
Job Eligibility
     ↓
Job Assignment
     ↓
Daily Work
     ↓
Compliance Monitoring
```

This ensures that worker safety training directly affects what industrial work the worker is permitted to perform.

---

# 30. Expected Impact

SurakshaSetu can help industrial organizations:

- Improve practical safety understanding
- Reduce dependency on classroom-only training
- Provide standardized training for contract workers
- Make training available in regional languages
- Verify certifications instantly
- Detect expired certifications
- Prevent unsafe job assignments
- Track worker compliance
- Provide offline safety training
- Reduce training infrastructure costs
- Connect safety with daily operations
- Improve audit readiness

---

# 31. SIH MVP Scope

For the Smart India Hackathon MVP, the project can demonstrate:

### Mobile Application

- Worker login
- Language selection
- Worker dashboard
- Two complete AR safety modules
- Offline training content
- Assessment engine
- QR certificate generation
- Certificate verification
- Job assignment
- Job eligibility based on certification
- Daily tasks
- Attendance
- Leave
- Salary information
- Emergency SOS

### Admin Dashboard

- Worker management
- Training assignment
- Assessment tracking
- Certificate management
- Job creation
- Job assignment
- Worker eligibility
- Attendance
- Compliance dashboard

### Backend

- Express.js REST APIs
- PostgreSQL
- Authentication
- Training APIs
- Assessment APIs
- Certificate APIs
- Workforce APIs
- Compliance logic

---

# 32. Future Scope

Future versions can include:

- AI-based personalized training
- Computer-vision PPE detection
- AI-generated safety quizzes
- Voice-based assistant
- Predictive incident-risk analysis
- IoT gas-sensor integration
- Wearable-device integration
- Equipment QR scanning
- Geofenced safety alerts
- Skill-based job recommendations
- Government certification integration
- Company ERP integration
- Contractor-management system
- Advanced safety analytics

---

# 33. Conclusion

SurakshaSetu transforms industrial safety training from a passive classroom activity into a continuous digital safety ecosystem.

Instead of treating training, certification, job assignment, attendance, and worker management as separate systems, SurakshaSetu connects them.

The platform follows the complete worker lifecycle:

```text
LEARN
  ↓
PRACTICE
  ↓
ASSESS
  ↓
CERTIFY
  ↓
BECOME ELIGIBLE
  ↓
WORK
  ↓
TRACK
  ↓
STAY COMPLIANT
```

The goal is simple:

**No worker should be assigned to a high-risk industrial task without the training and certification required to perform that task safely.**
