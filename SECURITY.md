# Security Policy

## Supported Versions

We provide security updates for all current versions of SMPStats.

We recommend always using the latest stable release.

## Reporting a Vulnerability

If you discover a security vulnerability in SMPStats, please report it responsibly:

### How to Report

1. **DO NOT** open a public issue
2. Email the maintainer directly or use GitHub Security Advisories
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Affected versions
   - Potential impact
   - Suggested fix (if any)

### What to Expect

- **Response Time**: Within 48 hours
- **Assessment**: We'll evaluate severity and impact
- **Fix Timeline**: 
  - Critical: 1-3 days
  - High: 1 week
  - Medium: 2 weeks
  - Low: Next release cycle
- **Disclosure**: Coordinated disclosure after fix is released

### Security Features in Releases

All releases include:
- **SHA256 checksums** for integrity verification
- **GPG signatures** for authenticity (stable releases)
- **SBOM** (Software Bill of Materials) for dependency tracking
- **Build provenance** attestations via GitHub

## Artifact Verification

### Verify Checksums

```bash
sha256sum -c SMPStats-vX.Y.Z.jar.sha256
```

## GPG Signature Verification

Releases are signed with the key ID: `61C5D541FA4681DA5AEBACB1A353FBFE821B439E`

```bash
# Import public key from keyserver
gpg --keyserver keyserver.ubuntu.com --recv-keys 61C5D541FA4681DA5AEBACB1A353FBFE821B439E

# Or from file:
gpg --import docs/gpg-public-key.asc

# Verify release
gpg --verify SMPStats-vX.Y.Z.jar.asc SMPStats-vX.Y.Z.jar
```

## Dependency Security

- Dependencies are tracked in `pom.xml`
- SBOM (CycloneDX format) included with each release
- Automated dependency updates via Dependabot
- Regular security scans with CodeQL

## Best Practices for Users

1. **Download from official sources only**: GitHub Releases and Modrinth
2. **Verify checksums**: Always check SHA256 before installation
3. **Verify signatures**: Check GPG signatures for stable releases
4. **Keep updated**: Install security patches promptly
5. **Secure your API**: Change default API key in `config.yml`
6. **Limit API exposure**: Bind to localhost if not needed externally
7. **Regular backups**: Back up your stats database

## Security in Configuration

### API Security

The HTTP API uses API key authentication:

```yaml
api:
  enabled: true
  bind_address: "127.0.0.1"  # Localhost only for security
  port: 8765
  api_key: "ChangeThisToASecureRandomKey"  # CHANGE THIS!
```

**Important**:
- Change the default API key immediately
- Use a strong, random key (e.g., `openssl rand -base64 32`)
- Bind to `127.0.0.1` unless external access is required
- Use reverse proxy with TLS for production

### Database Security

- SQLite database stored in plugin data folder
- Ensure proper file permissions (readable only by server process)
- Regular backups recommended
- No external database credentials required

## Known Security Considerations

### HTTP API

- ⚠️ No built-in TLS/HTTPS support
  - **Mitigation**: Use reverse proxy (nginx/Caddy) with TLS
- ⚠️ API key sent in headers
  - **Mitigation**: Always use TLS in production
- ⚠️ No rate limiting
  - **Mitigation**: Implement at reverse proxy level

### Data Privacy

- Player UUIDs and statistics are stored
- Death replay stores inventory snapshots
- Moment data includes coordinates
- No automatic data retention/deletion
- Server owners responsible for GDPR/privacy compliance

## Security Updates

Security updates are released as:
- **Patch releases** for backward-compatible fixes
- **Advisories** published for vulnerabilities
- **Changelogs** document security fixes

Subscribe to releases on GitHub to stay informed.

## Acknowledgments

We appreciate security researchers who responsibly disclose vulnerabilities. Contributors will be acknowledged in release notes (unless anonymity is requested).

---

**Last Updated**: November 26, 2025  
**Contact**: Create a security advisory on GitHub or email me at `robin@nurrobin.de`
