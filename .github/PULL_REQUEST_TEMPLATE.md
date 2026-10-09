## O que muda

<!-- Resumo em 1–3 frases. Referencie a issue com "Closes #N", se houver. -->

## Tipo de mudança

- [ ] 🐞 Correção de bug
- [ ] ✨ Nova funcionalidade
- [ ] 🧹 Refatoração (sem mudança de comportamento)
- [ ] 📝 Documentação / CI

## Checklist

- [ ] `./gradlew testDebugUnitTest` passa (253 testes)
- [ ] `./gradlew lintDebug` termina sem erros
- [ ] CI verde neste PR
- [ ] Nenhuma permissão nova no `AndroidManifest.xml` (projeto: só `CAMERA` + `VIBRATE`)
- [ ] Nenhuma credencial no diff (`keystore.properties`, `*.jks`, senhas)
- [ ] OCR/alérgenos continuam apresentados como **sugestões**, com confirmação manual
- [ ] Docs atualizadas quando aplicável (`README.md`, `CHANGELOG.md`, `CHECKLIST_QA.md`)
