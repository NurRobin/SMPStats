# SMPStats Scripts

Dieses Verzeichnis enthält Helper-Scripts für Entwicklung und Releases.

---

## 📦 Release-Management

### `set-version.sh` - Version zentral setzen

Setzt die Version an allen erforderlichen Stellen.

**Usage:**
```bash
./scripts/set-version.sh 0.7.0
./scripts/set-version.sh 1.0.0-beta.1
```

**Was es tut:**
- ✅ Aktualisiert `pom.xml` (via Maven)
- ✅ Aktualisiert `src/main/resources/plugin.yml`
- ✅ Validiert Versionsformat
- ✅ Zeigt Änderungen an

**Nächste Schritte nach dem Script:**
1. `git add pom.xml src/main/resources/plugin.yml`
2. `git commit -m "chore: bump version to X.Y.Z"`
3. `git push origin main`
4. Auto-Release erstellt automatisch den Draft! 🚀

---

## ✅ Release-Verifikation

### `verify-release.sh` - Release-Artefakte verifizieren

Für **Benutzer** zum Verifizieren von Downloads.

**Usage:**
```bash
# Downloads im aktuellen Verzeichnis
./scripts/verify-release.sh v0.7.0

# Downloads in anderem Verzeichnis
./scripts/verify-release.sh v0.7.0 ~/Downloads
```

**Was es prüft:**
- ✅ Alle Dateien vorhanden
- ✅ SHA256-Checksums
- ✅ GPG-Signaturen (wenn vorhanden)
- ✅ SBOM-Datei
- ✅ Build Provenance (mit GitHub CLI)

**Ausgabe:**
```
🔍 Verifying SMPStats v0.7.0
================================

📁 Checking for required files...
  ✓ Found: SMPStats-v0.7.0.jar
  ✓ Found: SMPStats-v0.7.0.jar.sha256
  ✓ Found: SMPStats-v0.7.0.jar.asc
  ...

🔐 Verifying SHA256 checksum...
  ✓ Checksum verification PASSED

🔑 Verifying GPG signature...
  ✓ GPG signature verification PASSED

✅ Verification complete for SMPStats v0.7.0
```

---

## 🎯 Workflow

### Typischer Release-Ablauf

```bash
# 1. Version setzen
./scripts/set-version.sh 0.7.0

# 2. Committen & pushen
git add pom.xml src/main/resources/plugin.yml
git commit -m "chore: bump version to 0.7.0"
git push origin main

# 3. Warten (~2-3 Min) - Auto-Release läuft

# 4. Draft Release veröffentlichen auf GitHub

# Fertig! 🎉
```



---

## 📚 Weitere Informationen

- **Release Process:** `docs/RELEASE_PROCESS.md`
- **System-Übersicht:** `docs/AUTO_RELEASE_SUMMARY.md`
- **Sicherheit & Verifikation:** `SECURITY.md`

---

## 🔧 Für Entwickler

Alle Scripts sind:
- ✅ Executable (`chmod +x`)
- ✅ Bash mit `set -e` (fail on error)
- ✅ Kommentiert und dokumentiert
- ✅ Mit Fehlerprüfung

Anpassungen willkommen! Pull Requests gerne an `main` Branch.
