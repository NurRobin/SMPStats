#!/bin/bash
# set-version.sh - Zentrales Script zum Setzen der SMPStats Version
# 
# Usage: ./scripts/set-version.sh <version>
# Example: ./scripts/set-version.sh 0.7.0

set -e

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 0.7.0"
    echo
    echo "Current version:"
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "Unknown"
    exit 1
fi

# Validate version format
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?$ ]]; then
    echo "❌ Ungültiges Versionsformat!"
    echo "Format: X.Y.Z oder X.Y.Z-suffix"
    echo "Beispiele: 0.7.0, 1.0.0, 0.7.0-beta.1"
    exit 1
fi

echo "🔄 Setze Version auf: $VERSION"
echo "=============================="
echo

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

# Check if files exist
if [ ! -f "pom.xml" ]; then
    echo "❌ pom.xml nicht gefunden!"
    exit 1
fi

if [ ! -f "src/main/resources/plugin.yml" ]; then
    echo "❌ plugin.yml nicht gefunden!"
    exit 1
fi

echo "📝 Aktualisiere pom.xml..."
mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false -q

if [ $? -eq 0 ]; then
    echo "   ✓ pom.xml aktualisiert"
else
    echo "   ❌ Fehler beim Aktualisieren von pom.xml"
    exit 1
fi

echo "📝 Aktualisiere plugin.yml..."
# Use sed to update version in plugin.yml
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^version: .*/version: $VERSION/" src/main/resources/plugin.yml
else
    # Linux
    sed -i "s/^version: .*/version: $VERSION/" src/main/resources/plugin.yml
fi

if [ $? -eq 0 ]; then
    echo "   ✓ plugin.yml aktualisiert"
else
    echo "   ❌ Fehler beim Aktualisieren von plugin.yml"
    exit 1
fi

echo
echo "✅ Version erfolgreich auf $VERSION gesetzt!"
echo
echo "Geänderte Dateien:"
git diff --name-only pom.xml src/main/resources/plugin.yml

echo
echo "📋 Änderungen:"
echo
git diff pom.xml src/main/resources/plugin.yml

echo
echo "Nächste Schritte:"
echo "  1. Überprüfe die Änderungen"
echo "  2. Commit die Änderungen:"
echo "     git add pom.xml src/main/resources/plugin.yml"
echo "     git commit -m 'chore: bump version to $VERSION'"
echo "  3. Push changes:"
echo "     git push"
echo "  4. Das Release wird automatisch erstellt sobald die Changes auf main sind! 🚀"
echo
