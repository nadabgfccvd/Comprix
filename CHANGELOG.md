# Changelog

Todas as mudanças notáveis do Comprix ficam registradas aqui, com base nas notas de cada [release](https://github.com/nadabgfccvd/Comprix/releases). O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); as versões seguem [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.6.0] — 2026-10-10

### Adicionado
- **Gasto por mercado** (Histórico): gráfico novo "onde o dinheiro foi", somando o total pago por loja no período escolhido — a leitura que faltava ao lado de "gasto por categoria": qual mercado fica com a maior fatia da conta, com valor absoluto e percentual por loja.
- **Compartilhar resumo da compra** (folha de celebração): o complemento do "compartilhar lista" da ida — texto plano com data, itens, total, economia e o pedaço de cada loja na compra mista, pronto para colar no grupo da família.
- **Exportar a matriz de comparação em CSV** (Comparar estabelecimentos): a tabela inteira em planilha — uma linha por produto, uma coluna por loja, célula "-" sem preço e coluna final "melhor loja". Mesmo padrão das outras exportações (BOM, `;`, dinheiro pt-BR).

### Refinado
- **Total estimado animado**: ao marcar item ou gravar preço, o número da doca caminha até o novo valor em vez de piscar.
- **Item comprado afunda suave**: a opacidade do cartão marcado agora é animada (220 ms) em vez de trocar num frame.
- **Barra de rolagem fina** na lista de compras e no histórico: aparece enquanto rola e some sozinha — orientação em listas longas sem ocupar espaço.

### Interno
- **Room 2.6.1 → 2.8.5** (runtime, ktx e compiler via PRs do Dependabot, CI verde).
- Dependabot com regras de `ignore` para atualizações que exigem troca de toolchain (AGP 9/Gradle 9, compileSdk 36, Kotlin 2.1+) — os PRs continuam virando issues para não se perderem.
- 269 testes unitários (eram 257).

## [1.5.0] — 2026-10-09

### Adicionado
- **CSV do histórico de preços** (Configurações → Backup): cada anotação do acervo em um arquivo — `produto;loja;preco;quantidade;data`, com BOM e separador `;` para abrir direto no Excel/Calc. Responde "onde e quando esse item custou menos" fora do app.
- **Widget da lista mostra o total estimado**: linha nova "≈ R$ X estimados" (some quando não há preço anotado); pré-visualização real do widget no seletor (Android 12+) e descrição acessível.
- **Linha de média no gráfico de preço do produto**: tracejada em verde, com legenda "Média: R$ X" — responde "estou pagando na média ou acima dela?" de relance, junto do selo de menor preço.

### Interno
- Workflow de **release** (`.github/workflows/release.yml`): ao empurrar uma tag `v*`, o CI roda os testes, builda `assembleRelease` e publica os APKs na release — assinados automaticamente se os segredos de assinatura estiverem configurados (a chave nunca toca o repositório), não assinados caso contrário.

## [1.4.2] — 2026-10-09

### Segurança
- **Rotação da chave de assinatura.** Até a v1.4.1, a chave de assinatura era gerada pelo build e **versionada no repositório** com senha fixa — qualquer pessoa podia produzir um APK com a identidade do app. A chave antiga foi aposentada como comprometida, removida do repositório **e de todo o histórico git**. A partir daqui, o app é assinado por chave RSA-4096 **privada**, fora do repositório, carregada via `keystore.properties` (gitignored); sem as credenciais, a release sai não assinada. Detalhes em [`SECURITY.md`](SECURITY.md) e [`docs/ROTACAO-DE-CHAVE-v1.4.2.md`](docs/ROTACAO-DE-CHAVE-v1.4.2.md).
- **Migração obrigatória (uma vez só):** com a troca de assinatura, o Android bloqueia atualizar por cima — **backup → desinstalar → instalar v1.4.2 → restaurar**. Nada se perde (listas, produtos, preços, histórico e restrições voltam pelo backup).

### Adicionado
- **CI no GitHub Actions**: 253 testes unitários + lint + build de debug a cada push/PR, com badge no README e artefatos de diagnóstico em falha.
- `SECURITY.md` com política de segurança e divulgação da rotação.
- **OCR e alertas de alérgenos apresentados como sugestões**: banner permanente na tela de revisão da câmera ("não substitui a leitura do rótulo físico") e nota na ficha do produto (falso positivo/negativo). A documentação técnica (`ExtratorDeRotulo`, `VerificadorDeAlergias`) deixa claro que as confianças são pesos heurísticos fixos por campo — não probabilidades calibradas — e que a confirmação manual segue obrigatória.

### Corrigido
- 4 erros de lint `FullBackupContent` (excludes redundantes nas regras de backup). O lint voltou a **bloquear** o build de release (`abortOnError = true`, sem `checkReleaseBuilds = false`).

### Documentação
- `CHECKLIST_QA.md` reconciliado: os **253 testes** da versão atual ficam explícitos, e os 91 citados antes são marcados como histórico da entrega v1.0.

## [1.4.1] — 2026-10-09

### Adicionado
- **APKs por ABI**: `arm64-v8a` caiu de 67 MB → **21 MB (−68%)**; `armeabi-v7a` −77%. O universal continua disponível como fallback.

### Corrigido
- Auditoria de UX/acessibilidade com 21 correções: estados de carregamento em todas as telas, confirmações "PERIGO" para ações destrutivas, textos canônicos pt-BR ("Não tinha nesta loja", "Excluir" vs "Apagar"), alvos de toque de 48 dp, semântica para TalkBack e ícones coerentes (glifo de carregamento animado no scanner).

## [1.4.0] — 2026-10-09

### Adicionado
- Preços de kit/pack refletidos nos totais da lista.
- Painel de validades com código de cor (vencido / ≤ 7 dias / 8–30 dias).
- Backup/restauração aprimorado.

## [1.3.1] — 2026-10-09

### Corrigido
- Reconhecimento de preço com padrão decimal canônico; rótulo ilegível não extrai mais preço incorreto via OCR.

## [1.3.0] — 2026-10-09

### Corrigido
- As 8 correções reportadas por usuários, mais todo o acúmulo de correções v1.1.0 → v1.3.0.

## [1.2.0] — 2026-10-09

### Corrigido
- Melhorias e correções da rodada de reconstrução.

## [1.1.0] — 2026-10-09

### Corrigido
- Primeiras correções após a reconstrução do app. Keystore original preservada (instala por cima sem desinstalar).

## [1.0] — 2026-10-09

### Primeira versão arquivada
- Fase v1.0 da reconstrução do Comprix (versionCode 1, 91 testes). Builds arquivadas em `.tgz` preservando os nomes originais dos APKs (`tar -xzf` para extrair).

[1.6.0]: https://github.com/nadabgfccvd/Comprix/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/nadabgfccvd/Comprix/compare/v1.4.2...v1.5.0
[1.4.2]: https://github.com/nadabgfccvd/Comprix/compare/v1.4.1...v1.4.2
[1.4.1]: https://github.com/nadabgfccvd/Comprix/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/nadabgfccvd/Comprix/compare/v1.3.1...v1.4.0
[1.3.1]: https://github.com/nadabgfccvd/Comprix/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/nadabgfccvd/Comprix/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/nadabgfccvd/Comprix/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/nadabgfccvd/Comprix/compare/v1.0...v1.1.0
[1.0]: https://github.com/nadabgfccvd/Comprix/releases/tag/v1.0
