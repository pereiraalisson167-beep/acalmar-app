#!/usr/bin/env bash
# Publica o APK gerado como um Release do GitHub (arquivo acalmar.apk).
set -euo pipefail

APK="$(ls app/build/outputs/apk/release/*.apk | head -n 1)"
VERSION="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -n 1 | cut -d'"' -f2)"
BUILD="${GITHUB_RUN_NUMBER}"
cp "$APK" acalmar.apk
ls -la acalmar.apk

if [ "${SIGNED:-false}" = "true" ]; then
  gh release create "v${VERSION}-${BUILD}" acalmar.apk \
    --target "${GITHUB_SHA}" \
    --title "Acalmar ${VERSION} (versão ${BUILD})" \
    --notes "Baixe o arquivo **acalmar.apk** abaixo e abra no celular para instalar ou atualizar. Os dados do app são mantidos na atualização." \
    --latest
else
  gh release create "teste-${BUILD}" acalmar.apk \
    --target "${GITHUB_SHA}" \
    --title "TESTE ${VERSION} (${BUILD}) - assinatura temporária" \
    --notes "Versão apenas para teste. **Não instale no celular da Karina**: esta versão não aceita atualização por cima. Cadastre o segredo KEYSTORE_PASSWORD no repositório para gerar a versão definitiva." \
    --prerelease
fi
