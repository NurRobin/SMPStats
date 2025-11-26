#!/bin/bash
# verify-release.sh - Verify SMPStats release artifacts
#
# Usage: ./scripts/verify-release.sh vX.Y.Z [path/to/downloads]

set -e

VERSION=$1
DOWNLOAD_DIR=${2:-.}

if [ -z "$VERSION" ]; then
    echo "Usage: $0 vX.Y.Z [download-dir]"
    echo "Example: $0 v0.7.0 ~/Downloads"
    exit 1
fi

echo "🔍 Verifying SMPStats $VERSION"
echo "================================"
echo

JAR_FILE="$DOWNLOAD_DIR/SMPStats-${VERSION}.jar"
CHECKSUM_FILE="$DOWNLOAD_DIR/SMPStats-${VERSION}.jar.sha256"
SIG_FILE="$DOWNLOAD_DIR/SMPStats-${VERSION}.jar.asc"
SBOM_FILE="$DOWNLOAD_DIR/SMPStats-${VERSION}.sbom.json"

# Check if files exist
echo "📁 Checking for required files..."
MISSING=0

for file in "$JAR_FILE" "$CHECKSUM_FILE"; do
    if [ ! -f "$file" ]; then
        echo "  ❌ Missing: $(basename "$file")"
        MISSING=1
    else
        echo "  ✓ Found: $(basename "$file")"
    fi
done

if [ -f "$SIG_FILE" ]; then
    echo "  ✓ Found: $(basename "$SIG_FILE")"
    HAS_SIG=1
else
    echo "  ⚠️  No GPG signature (may be pre-release)"
    HAS_SIG=0
fi

if [ -f "$SBOM_FILE" ]; then
    echo "  ✓ Found: $(basename "$SBOM_FILE")"
    HAS_SBOM=1
else
    echo "  ⚠️  No SBOM found"
    HAS_SBOM=0
fi

if [ $MISSING -eq 1 ]; then
    echo
    echo "❌ Missing required files. Please download all artifacts."
    exit 1
fi

echo

# Verify checksum
echo "🔐 Verifying SHA256 checksum..."
cd "$DOWNLOAD_DIR"
if sha256sum -c "SMPStats-${VERSION}.jar.sha256" 2>&1 | grep -q "OK"; then
    echo "  ✓ Checksum verification PASSED"
else
    echo "  ❌ Checksum verification FAILED"
    exit 1
fi
cd - > /dev/null

echo

# Verify GPG signature if present
if [ $HAS_SIG -eq 1 ]; then
    echo "🔑 Verifying GPG signature..."
    if gpg --verify "$SIG_FILE" "$JAR_FILE" 2>&1 | grep -q "Good signature"; then
        echo "  ✓ GPG signature verification PASSED"
    else
        echo "  ❌ GPG signature verification FAILED or key not trusted"
        echo "  Note: You may need to import the public key first"
    fi
    echo
fi

# Verify SBOM if present
if [ $HAS_SBOM -eq 1 ]; then
    echo "📋 Checking SBOM..."
    if command -v jq &> /dev/null; then
        COMPONENTS=$(jq -r '.components | length' "$SBOM_FILE")
        echo "  ✓ SBOM contains $COMPONENTS dependencies"
    else
        echo "  ✓ SBOM file present (install 'jq' for details)"
    fi
    echo
fi

# GitHub attestation verification
echo "🏗️  Verifying build provenance..."
if command -v gh &> /dev/null; then
    if gh attestation verify "$JAR_FILE" --repo NurRobin/SMPStats 2>&1 | grep -q "Verification succeeded"; then
        echo "  ✓ Build provenance verification PASSED"
    else
        echo "  ⚠️  Build provenance verification failed or not available"
    fi
else
    echo "  ⚠️  Install GitHub CLI (gh) to verify build provenance"
fi

echo
echo "================================"
echo "✅ Verification complete for SMPStats $VERSION"
echo
echo "The plugin JAR is ready to install: $JAR_FILE"
