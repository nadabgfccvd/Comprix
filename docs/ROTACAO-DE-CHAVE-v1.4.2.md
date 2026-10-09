# Rotação da chave de assinatura — v1.4.2

> **Para usuários do Comprix:** a partir da v1.4.2 o app é assinado por uma chave nova.
> O Android não deixa atualizar por cima de uma versão assinada por chave diferente.
> **O que fazer:** exporte o backup no app antigo → desinstale → instale a v1.4.2 → restaure o backup.
> Passo a passo completo na seção 1 abaixo. Nada se perde: o backup é um arquivo JSON local.

---

## 1. Guia do usuário: migrando da v1.4.1 (ou anterior) para a v1.4.2

1. **Abra o Comprix antigo** → **Configurações** → **Backup** → **Exportar backup**.
   Guarde o arquivo `comprix-backup-....json` num lugar seguro (Downloads, Google Drive, cabo USB — onde preferir).
2. **Desinstale** o app antigo.
3. **Instale** o APK `comprix-v1.4.2-...-release.apk` da [página de Releases](https://github.com/nadabgfccvd/Comprix/releases).
   (arm64-v8a é o recomendado para celulares de 2016 em diante; universal funciona em qualquer um.)
4. **Abra o app novo** → **Configurações** → **Backup** → **Restaurar backup** → escolha o arquivo do passo 1.
5. Pronto — listas, produtos, preços, histórico e restrições voltam exatamente como estavam.

**Por que isso é necessário:** o Android trata "chave de assinatura diferente" como app diferente por segurança — é o que impede, por exemplo, que alguém publique um Comprix falso por cima do seu. A troca de chave foi uma decisão de segurança (seção 2); o custo é essa migração manual, feita uma única vez.

## 2. O que aconteceu (transparência)

- Até a **v1.4.1**, o build de release gerava/assinava com uma keystore **versionada no próprio repositório** (`keystore/comprix-release.jks`) e com **senha fixa** (`comprix`) escrita no `build.gradle.kts`. Isso tornava a instalação de atualizações trivial, mas significava que **qualquer pessoa** podia gerar um APK com a mesma identidade do app.
- Essa chave é tratada como **comprometida** e foi aposentada: não assina mais nenhum release.
- O arquivo da keystore, os arquivos de backup que a continham e as senhas foram removidos **do repositório e de todo o histórico git** (reescrevemos o histórico e as tags).
- Um **APK já publicado é público por natureza**: as versões antigas continuam na página de Releases como registro histórico. Se você só instala APKs desta página e confia na identidade nova, não há ação além da migração da seção 1.

## 3. Como o release é assinado agora (mantenedores)

- A chave nova (**RSA 4096, validade 30 anos, alias `comprix-v2`**) vive **fora do repositório**.
- O build lê as credenciais de `keystore.properties` na raiz do projeto — arquivo **gitignored** (`.gitignore` bloqueia `keystore.properties`, `*.jks` e `*.keystore`):

  ```properties
  storeFile=/caminho/absoluto/para/comprix-release-v2.jks
  storePassword=...
  keyAlias=comprix-v2
  keyPassword=...
  ```

- **Sem** `keystore.properties`, `./gradlew assembleRelease` produz APK **não assinado** (útil para CI); `assembleDebug` e `testDebugUnitTest` não precisam de chave.
- Guardem a `.jks` e a senha **em cofre de senha** (bitwarden/1password/keepass) — se a chave nova vazar, a rotação seguinte exige outra migração manual dos usuários.

## 4. Checklist pós-rotação (mantenedores)

- [x] Keystore antiga removida do HEAD e de todo o histórico (`git filter-repo`)
- [x] Senhas hardcoded removidas do histórico (`garantirKeystoreDeRelease` aposentada)
- [x] `.gitignore` bloqueando `keystore.properties` / `*.jks` / `*.keystore`
- [x] Chave v2 gerada (RSA 4096) e mantida fora do repo
- [x] `build.gradle.kts` lê `keystore.properties`; release sem credenciais sai não assinado
- [x] CI (GitHub Actions) roda testes + lint + build sem precisar da chave
- [x] v1.4.2 assinada com a chave v2
- [x] `SECURITY.md` com a divulgação pública da rotação
- [ ] **Mantenedor:** armazenar a `.jks` v2 + senha em cofre de senha (o arquivo foi entregue por canal efêmero — baixe e apague o bin)
- [ ] **Mantenedor:** nunca assinar produção com a chave antiga novamente
