# Setting up branch protection on `main`

This isn't something that can be configured via a script or the GitHub API
tools available in this session — it's a repository setting that requires
admin access through GitHub's UI. Follow these steps.

## Where

1. Go to `https://github.com/rykerwilliams/ShelfTime/settings/branches`
2. Click **Add branch protection rule** (or **Add rule**)
3. Set **Branch name pattern** to `main`

## Recommended settings

- **Require a pull request before merging** — enabled.
  Matches how this repo already works: changes land on a feature branch
  first (e.g. `claude/upstream-issues-review-q3sdj1`), never pushed
  directly to `main`. This turns that habit into an enforced rule.

- **Require status checks to pass before merging** — enabled.
  Search for and select the **`build`** check — that's the job name from
  the `Build Debug APK` workflow (`.github/workflows/build-apk.yml`),
  which runs the unit test suite and then `assembleDebug`. This is the
  most important setting: it means nothing that fails to compile or
  fails a test can reach `main`.
  - Also enable **Require branches to be up to date before merging**
    alongside it, so a PR must incorporate the latest `main` before it's
    allowed to merge.

- **Do not allow force pushes** — enabled (leave force-push disabled).

- **Do not allow deletions** — enabled (protect `main` from being deleted).

## What to skip

- **Require approvals** — leave this **off**. This repo has a single
  maintainer, and GitHub does not allow a PR author to approve their own
  pull request — turning this on would lock you out of merging your own
  work. Revisit this if a second collaborator joins the project.

## After setup

Once the rule is active, verify it by confirming:
- A direct push to `main` from the command line is rejected.
- A PR into `main` shows the `build` check as required, and the merge
  button stays disabled until that check passes.
