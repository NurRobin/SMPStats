# Release Process

**Vollautomatisiertes Release-System für SMPStats**

## 🎯 Überblick

Das Release-System ist **vollständig automatisiert**. Du musst dich nur minimal damit beschäftigen!

**Automatisch:**
1. ✅ Version-Erkennung wenn `pom.xml` sich ändert
2. ✅ Tag wird automatisch erstellt
3. ✅ Build, Tests, Artefakte
4. ✅ Draft Release mit intelligenten Notes

**Manuell (minimal):**
1. Version setzen mit Script
2. Commit & Push
3. Draft Release veröffentlichen

---

## 🚀 Release erstellen (3 Schritte)

### Schritt 1: Version setzen

```bash
# Zentral die neue Version setzen
./scripts/set-version.sh 0.7.0

# Für Pre-releases:
./scripts/set-version.sh 0.7.0-beta.1
```

Das Script aktualisiert:
- ✅ `pom.xml`
- ✅ `src/main/resources/plugin.yml`

### Schritt 2: Committen & Pushen

```bash
git add pom.xml src/main/resources/plugin.yml
git commit -m "chore: bump version to 0.7.0"
git push origin main
```

### Schritt 3: Release veröffentlichen

1. ⏱️ **Warte 2-3 Minuten** - Auto-Release Workflow läuft
2. 📝 Gehe zu [Releases](https://github.com/NurRobin/SMPStats/releases)
3. ✏️ **Passe Draft an** (optional)
4. ✅ Klicke **"Publish release"**
5. **Fertig!** 🎉

---

## 🎨 Release-Typen (automatisch erkannt)

Der Workflow analysiert die Versionsänderung und erstellt passende Release-Notes:

### 🚀 Major Release (z.B. 0.6.0 → 1.0.0)
```bash
./scripts/set-version.sh 1.0.0
```
- **Titel:** "Major Release"
- **Emoji:** 🚀
- **Beschreibung:** Breaking Changes, große Änderungen
- **Empfehlung:** Ausführliche Release-Notes

### ✨ Minor Release (z.B. 0.6.0 → 0.7.0)
```bash
./scripts/set-version.sh 0.7.0
```
- **Titel:** "Feature Release"
- **Emoji:** ✨
- **Beschreibung:** Neue Features, Verbesserungen
- **Empfehlung:** Standard Release-Notes

### 🔧 Patch Release (z.B. 0.6.0 → 0.6.1)
```bash
./scripts/set-version.sh 0.6.1
```
- **Titel:** "Maintenance Release"
- **Emoji:** 🔧
- **Beschreibung:** Bugfixes, kleine Verbesserungen
- **Empfehlung:** Kurze Release-Notes

### ⚗️ Pre-Release (z.B. 0.7.0-beta.1)
```bash
./scripts/set-version.sh 0.7.0-beta.1
```
- **Automatisch als Pre-release markiert**
- **Keine GPG-Signierung**
- **Test-Version für Feedback**

---

## 📦 Release-Artefakte

Jeder Release enthält automatisch:

| Artefakt | Beschreibung |
|----------|--------------|
| `SMPStats-vX.Y.Z.jar` | Haupt-Plugin JAR |
| `*.jar.sha256` | SHA256-Prüfsumme zum Verifizieren |
| `*.jar.asc` | GPG-Signatur (nur stable releases) |
| `*.sbom.json` | Software Bill of Materials (Dependencies) |
| `*.sbom.json.sha256` | SBOM-Prüfsumme |
| `*.sbom.json.asc` | SBOM-Signatur (nur stable) |
| Build Provenance | GitHub Attestation (SLSA) |

---

## 🔐 Sicherheit & Verifikation

### SHA256-Prüfsumme verifizieren
```bash
sha256sum -c SMPStats-vX.Y.Z.jar.sha256
```

### GPG-Signatur verifizieren (wenn eingerichtet)
```bash
gpg --verify SMPStats-vX.Y.Z.jar.asc SMPStats-vX.Y.Z.jar
```

### Build Provenance verifizieren
```bash
gh attestation verify SMPStats-vX.Y.Z.jar --repo NurRobin/SMPStats
```

---

## 🎯 Workflow-Details

### Was passiert automatisch?

Wenn du zu `main` pushst und `pom.xml` geändert wurde:

1. **Version-Check** (~10 Sekunden)
   - Vergleicht aktuelle mit vorheriger Version
   - Prüft ob Tag schon existiert
   - Bestimmt Release-Typ (major/minor/patch)

2. **Tag erstellen** (~5 Sekunden)
   - Erstellt `vX.Y.Z` Tag
   - Pushed Tag zu GitHub

3. **Build & Test** (~60-90 Sekunden)
   - Maven Build mit Tests
   - JaCoCo Coverage-Check
   - Bricht ab bei Test-Failures

4. **Artefakte generieren** (~20 Sekunden)
   - SBOM erstellen
   - Checksums generieren
   - GPG-Signierung (wenn konfiguriert)
   - Build Provenance

5. **Draft Release erstellen** (~10 Sekunden)
   - Release-Notes nach Version-Typ
   - Alle Artefakte anhängen
   - Auto-generierte Changelog

6. **Changelog committen** (~10 Sekunden)
   - `docs/changelog/X.Y.Z.md` erstellen
   - Zu main pushen

**Gesamt:** ~2-3 Minuten

### Was passiert NICHT automatisch?

- ❌ Release veröffentlichen (bleibt Draft)
- ❌ Release-Notes anpassen/erweitern
- ❌ Ankündigungen posten

**Grund:** Du sollst finale Kontrolle behalten!

---

## 📋 Tipps & Best Practices

### Vor dem Release

✅ Alle geplanten Features/Fixes merged  
✅ Tests laufen lokal durch  
✅ Dokumentation aktualisiert  
✅ `Roadmap.md` angepasst  

### Release-Notes anpassen

Der Draft enthält:
- Auto-generierte PR-Liste
- Version-typ-spezifische Beschreibung
- Alle Artefakte

Füge hinzu:
- Highlights der wichtigsten Änderungen
- Breaking Changes (bei major)
- Bekannte Issues
- Upgrade-Hinweise

### Semantic Versioning

- **Major (X.0.0):** Breaking Changes
- **Minor (0.X.0):** Neue Features (backwards compatible)
- **Patch (0.0.X):** Bugfixes, kleine Improvements
- **Pre-release:** `-beta.1`, `-rc.1`, etc.

---

## 🐛 Troubleshooting

### "Tag already exists"
Der Workflow überspringt automatisch, wenn `vX.Y.Z` bereits existiert.

### "Tests failed"
Der Workflow bricht ab und erstellt KEIN Release. Behebe die Tests und pushe erneut.

### GPG-Signierung schlägt fehl
- Prüfe ob `GPG_PRIVATE_KEY` und `GPG_PASSPHRASE` Secrets existieren
- Bei Pre-releases wird GPG übersprungen (normal)

### Changelog nicht gepusht
Prüfe GitHub Actions Permissions: `contents: write` muss aktiv sein.

### Draft Release nicht gefunden
Gehe zu [Actions](https://github.com/NurRobin/SMPStats/actions) und prüfe den Auto-Release Workflow Log.

---

## 🎉 Das wars!

**Zusammenfassung:**
1. `./scripts/set-version.sh X.Y.Z`
2. `git add ... && git commit ... && git push`
3. Warte 2-3 Minuten
4. Release veröffentlichen

Einfacher geht's nicht! 🚀
