# PR Review - Specific File
Review the changes for the file: $ARGUMENTS

## Java Review Standards
When reviewing Java code, always check for:
- Security vulnerabilities (hardcoded secrets, SQL injection, unsafe deserialization)
- Null safety issues (NPE-prone chains, unguarded Optional)
- Performance problems (inefficient collections, N+1 patterns)
- Poor exception handling (swallowed exceptions, catching Throwable)
- Concurrency issues (unsynchronized shared state, improper volatile)
- Deprecated or removed APIs for the project Java version (Java 21)
- Design and best practice violations (SOLID, magic numbers, God classes)
- Opportunities to use modern Java 21 features (records, pattern matching, virtual threads)

## Required Output Format
Return a numbered list:
1. [High/Medium/Low] Issue Title
   - Location: <method or line>
   - Description: <what is wrong and why>
   - Fix: <how to fix it, with code snippet if helpful>

## Rules:
- Order: High → Medium → Low
- Skip categories with no issues found
- Always mention the specific method name or line number