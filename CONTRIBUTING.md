# Contributing and issue reports

Thank you for helping improve Utopia.

## Bug and crash reports

Before opening an issue, check whether the problem already exists. Include:

- Utopia pack version and whether the instance was modified
- launcher, operating system and Java version
- exact steps that reproduce the problem
- expected and actual behaviour
- `latest.log` or the matching file from `crash-reports/`

Do not paste an entire live game profile into the repository. Remove access
tokens, player addresses and other private information from logs before
uploading them.

## Performance reports

State the test location and scenario, render and simulation distance, shader
state, allocated memory, FPS or frame-time measurement, and whether the test
was repeated after restarting the client. Comparisons should change one
variable at a time whenever possible.

## Source changes

Use a focused branch and pull request. Generated Guidebook and Heracles files
must be changed through their generators rather than edited independently.
Pack exports and compiled JARs belong in GitHub Releases, not in Git history.
