# Workflow Optimierung - Build Artifact Sharing

## Problem
Vorher wurde das Projekt **mehrfach** gebaut:
- CI Workflow: `mvn verify`
- Coverage Workflow: `mvn verify` (nochmal!)
- Runtime Test: `mvn package -DskipTests` (nochmal!)
- Auto-Release: `mvn verify` (nochmal!)

Das war **ineffizient** und **langsam** ⏱️

## Lösung: Zentraler Build Workflow

### Neue Struktur

```
build.yml (reusable workflow)
    ↓
    ├── ci.yml (nutzt Build)
    ├── runtime-test.yml (nutzt Build)
    └── auto-release.yml (nutzt Build)
```

### `build.yml` - Zentraler Build
Ein **wiederverwendbarer Workflow** der:
- ✅ Einmal baut (mit oder ohne Tests)
- ✅ Artifacts hochlädt (JAR + Coverage)
- ✅ Optional Coverage zu Codecov hochlädt
- ✅ Version aus `pom.xml` ausliest

**Inputs:**
- `upload-coverage`: Boolean - Coverage hochladen?
- `skip-tests`: Boolean - Tests überspringen?

**Outputs:**
- `artifact-name`: Name des Artifacts
- `version`: Projekt Version

### Optimierte Workflows

#### `ci.yml` - Vereinfacht
```yaml
jobs:
  build:
    uses: ./.github/workflows/build.yml
    with:
      upload-coverage: true
    secrets:
      CODECOV_TOKEN: ${{ secrets.CODECOV_TOKEN }}
```

**Vorteile:**
- ✅ Jetzt auch auf `main` branch (Codecov Updates!)
- ✅ Nur noch 3 Zeilen statt 50+
- ✅ Kein doppelter Build mehr

#### `runtime-test.yml` - Artifact Reuse
```yaml
jobs:
  build:
    uses: ./.github/workflows/build.yml
    with:
      skip-tests: true  # Tests nicht nötig für Runtime Test
  
  runtime-test:
    needs: [build]
    # Nutzt Artifact vom Build Job
```

**Vorteile:**
- ✅ Kein separater Build mehr
- ✅ 30+ Zeilen gespart
- ✅ Schneller durch Skip Tests

#### `auto-release.yml` - Build Reuse
```yaml
jobs:
  build:
    uses: ./.github/workflows/build.yml
  
  create-release:
    needs: [check-version, build]
    steps:
      - name: Download built artifact
        uses: actions/download-artifact@v4
```

**Vorteile:**
- ✅ Nutzt bereits getestetes Artifact
- ✅ Kein separates `mvn verify` mehr
- ✅ Schnellerer Release-Prozess

### Gelöschte Workflows
- ❌ `coverage.yml` - Integriert in CI

## Vorteile der Optimierung

### 1. **Geschwindigkeit** ⚡
- **Vorher:** 4x Build (CI + Coverage + Runtime + Release)
- **Jetzt:** 1x Build, Rest nutzt Artifacts
- **Ersparnis:** ~75% Build-Zeit

### 2. **Codecov auf main Branch** 📊
- **Problem:** Coverage lief nur auf PRs, nicht auf `main`
- **Lösung:** CI läuft jetzt auch auf `main` → Coverage Updates!

### 3. **Wartbarkeit** 🛠️
- Zentraler Build Code
- Änderungen nur an einer Stelle
- Konsistente Build-Parameter

### 4. **Kosten** 💰
- Weniger GitHub Actions Minutes
- Weniger redundante Builds

## Verwendung

### Neuen Workflow hinzufügen
```yaml
jobs:
  build:
    uses: ./.github/workflows/build.yml
    with:
      upload-coverage: false  # Optional
      skip-tests: false       # Optional
    secrets:
      CODECOV_TOKEN: ${{ secrets.CODECOV_TOKEN }}  # Optional
  
  your-job:
    needs: [build]
    steps:
      - uses: actions/download-artifact@v4
        with:
          name: ${{ needs.build.outputs.artifact-name }}
```

### Artifact Struktur
```
SMPStats-${{ github.run_id }}/
├── SMPStats.jar
├── site/jacoco/
│   ├── jacoco.xml
│   └── ...
└── jacoco.exec
```

## Migration Checklist

- [x] Zentralen Build Workflow erstellt
- [x] CI auf Build umgestellt
- [x] Runtime Test auf Build umgestellt
- [x] Auto-Release auf Build umgestellt
- [x] Coverage Workflow gelöscht (in CI integriert)
- [x] CI auch auf main Branch aktiviert
- [x] Parallel Builds aktiviert (-T 1C)
- [x] Path Filters für alle Workflows

## Ergebnis

**Vorher:**
```
PR → CI (build) → Coverage (build) → Runtime Test (build) ❌
                                                           Langsam, redundant
```

**Jetzt:**
```
PR → Build (1x) → CI (reuse) → Runtime Test (reuse) ✅
                              → Coverage (inkl.)     Schnell, effizient
```

🎉 **Build-Zeit reduziert, Codecov funktioniert auf main, Code wartbarer!**
