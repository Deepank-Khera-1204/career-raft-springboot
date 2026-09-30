# Career Raft — Master Resume

`resume.tex` is the candidate's source-of-truth LaTeX resume supplied for Career Raft.

## Mutation policy

Career Raft may tailor factual, content-level fields only when the candidate evidence supports the change.

### Protected by default

- document class and packages
- margins and page geometry
- typography and formatting macros
- section formatting and ordering
- contact information and links
- employer/title/date facts
- education facts
- numerical claims and metrics unless sourced from verified evidence

### Tailorable by the resume engine

- role headline when supported by the target role
- ordering and wording of experience bullets
- project selection and ordering
- project bullet wording
- skill ordering/selection when supported by evidence
- certificate ordering

Every generated resume must:

1. map factual claims to verified evidence IDs;
2. pass the LaTeX compiler;
3. pass PDF/page-count validation;
4. be stored with the job ID and generation metadata.
