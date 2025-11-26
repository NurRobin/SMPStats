---
name: Release Checklist
about: Checklist for preparing and executing a release
title: 'Release v[VERSION]'
labels: ['release', 'chore']
assignees: ''
---

## Release Checklist for v[VERSION]

### Pre-release Preparation

- [ ] All planned features/fixes are merged to `main`
- [ ] All tests are passing on `main`
- [ ] Code coverage is satisfactory (>70%)
- [ ] Documentation is up to date
- [ ] `ROADMAP.md` is updated if needed
- [ ] No critical bugs in issue tracker

### Version Update

- [ ] Run version bump workflow OR manually update versions
  - [ ] `pom.xml` version updated
  - [ ] `src/main/resources/plugin.yml` version updated
- [ ] Version bump PR merged to `main`

### Release Execution

- [ ] Create and push version tag: `git tag vX.Y.Z && git push origin vX.Y.Z`
- [ ] Verify release workflow completes successfully
- [ ] Check all artifacts are attached to release
  - [ ] `SMPStats-vX.Y.Z.jar`
  - [ ] `SMPStats-vX.Y.Z.jar.sha256`
  - [ ] `SMPStats-vX.Y.Z.jar.asc` (if not pre-release)
  - [ ] `SMPStats-vX.Y.Z.sbom.json`
  - [ ] `SMPStats-vX.Y.Z.sbom.json.sha256`
  - [ ] `SMPStats-vX.Y.Z.sbom.json.asc` (if not pre-release)

### Post-release Verification

- [ ] Download and verify checksums
- [ ] Test plugin installation on test server
- [ ] Verify changelog was auto-generated in `docs/changelog/`
- [ ] Verify release notes on GitHub are accurate
- [ ] Update release notes if auto-generation missed anything

### Communication (Optional)

- [ ] Announce release in Discord/community channels
- [ ] Update any external documentation/wikis
- [ ] Close related milestone (if using milestones)

### For Pre-releases Only

- [ ] Tag follows format `vX.Y.Z-beta.N` or `vX.Y.Z-rc.N`
- [ ] Release is marked as pre-release on GitHub
- [ ] Include testing instructions in release notes
- [ ] Note any known issues or limitations

---

**Release Type**: [ ] Stable [ ] Pre-release  
**Version Type**: [ ] Major [ ] Minor [ ] Patch  
**Target Date**: [DATE]

## Notes

[Add any additional notes or special considerations for this release]
