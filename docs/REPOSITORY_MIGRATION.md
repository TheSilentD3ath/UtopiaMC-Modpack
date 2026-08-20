# Repository migration plan

The previous repository content was an incomplete client-profile export. The
repository will be brought up to date in controlled phases so that generated
files, binaries, private runtime state and source code are not mixed together.

1. Establish documentation, contribution rules and ignore rules; remove
   accidental runtime files.
2. Import maintained source projects after checking their repository and
   license boundaries.
3. Import canonical configuration, Guidebook and quest generator sources.
4. Add reproducible validation and release-building automation.
5. Publish verified `.mrpack` files and other binaries through GitHub Releases,
   then triage existing issues against those releases.

Each phase should be reviewed independently. The main branch should describe
only what has actually been imported and verified.
