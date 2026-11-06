# Work log — agente de desenvolvimento autônomo

Registro de iterações do agente autônomo que mantém este repositório. Cada entrada traz:
data, foco escolhido, mudanças, verificações executadas (com resultado), estado no GitHub
e a próxima tarefa útil. Mantido em pt-BR, no padrão dos demais documentos.

---

## 2026-11-06 — Sessão 2, iteração 3 (agente autônomo): filtro por loja no Histórico

**Contexto e foco.** CI do main verde (4 runs seguidas), sem issues acionáveis — a única
aberta (#38) fica no mantenedor: anexar os APKs assinados na release v1.7.0 (artifact
`apks-release-nao-assinados-v1.7.0` expira em ~14 dias; o keystore, por design, não está
no repo). Sem defeitos e sem PRs pendentes, a prioridade foi o enhancement sugerido na
iteração anterior: **filtro por loja no Histórico**, complemento natural da busca do PR #35.

**Mudanças (PR #39, mergeado).**
- `HistoricoScreen`: linha de pastilhas logo abaixo de "Buscar compras" — "Todas (N)" +
  uma pastilha por loja com contagem, mesma receita das categorias da Lixeira
  (`PastilhaSelecionavel`, toque na ativa recolhe). Rótulos idênticos aos dos cartões:
  "Mercado do Bairro", "Mercado do Bairro + 2 loja(s)" (compras multi-loja ficam agrupadas
  no rótulo combinado — preveível).
- O cartão "Nada encontrado" cobre busca e loja: a mensagem diz qual filtro apertou
  (só loja, só busca, ou os dois) e o botão virou "Limpar filtros", que abre os dois.
- `FiltroDeHistorico` (domain puro): pastilhas ordenadas por contagem (empate alfabético),
  casamento por rótulo exato e interseção **loja E busca** com o `TextoUtil.correspondeBusca`
  compartilhado. Documenta por que filtro por rótulo e não por id de loja.
- `HistoricoViewModel`: `lojaSelecionada`/`definirLoja`/`limparFiltros` + `lojasDisponiveis`
  derivado do estado (acompanha troca de período sem estado duplicado). Analíticas e
  números do topo seguem sobre TODAS as compras — filtro afina a lista, nunca reescreve
  a estatística.

**Verificação.** `./gradlew testDebugUnitTest` → **334 testes, 0 falhas** (eram 321;
13 novos em `FiltroDeHistoricoTest`, todos na JVM). `./gradlew lintDebug` e
`./gradlew assembleDebug` → verdes. Nota de ambiente: uma execução falhou com
`OutOfMemoryError: Metaspace` (sandbox com ~4 GB e servidor de dev da infra consumindo
memória); resolver repetindo com `-Pkotlin.daemon.jvm.options=-Xmx512m,...` via linha de
comando, sem tocar no `gradle.properties` do repo. CI do PR #39 verde no runner; main
verde após o merge. Diff revisado: sem segredos, sem mudança em dados/backup.

**Coordenação.** Arquivos disjuntos dos PRs recentes (#34–#37); sem colisões. Nota de
ferramenta: ao criar o PR via `gh.sh`, o corpo JSON vai direto no 3º argumento (sem o
`-d` do curl) — duas tentativas iniciais falharam com "Problems parsing JSON" por esse
detalhe de uso, corrigido na terceira.

**Estado do GitHub.** main em CI verde; 0 issues acionáveis (#38 é do mantenedor); PRs
abertos: nenhum; PRs #34, #35, #36, #37 e #39 mergeados; release v1.7.0 publicada,
seguindo sem APKs assinados (ver #38). Branch remota de feature apagada após o merge.

**Limitação registrada (higiene de branches).** Tentativa pós-merge de apagar as 19
branches remotes cujos PRs já foram mergeados (`git branch -r` mostrava 25 refs, das
quais 19 ligadas a PRs `merged=True` e 5 a PRs fechados sem merge, mantidas de propósito).
A API recusou todas com "Repository rule violations found — Cannot delete this branch":
as regras do repositório exigem permissão de administração que o fine-grained PAT do
agente não tem (e não deve ter). Nada foi contornado; as branches ficam para o mantenedor
apagar ("Restore/Delete branch" de cada PR) — o conteúdo de todas já está no main.

**Próximo passo útil.** (1) O mantenedor anexar os APKs assinados na release v1.7.0
(issue #38 — fora do alcance do agente, exige o keystore). (2) Melhorias futuras de UX
sugeridas: busca por sinônimo na exploração do catálogo (coluna `sinonimos` já existe no
acervo) ou marcação de favorito na lista de produtos; na Lixeira, a busca já convive com
o filtro de categoria. (3) Na próxima release (v1.8.0), atualizar CHECKLIST_QA e contagem
de testes (334 no "Não lançado").

---

## 2026-11-06 — Sessões anteriores (backfill, resumo)

**Foco:** estabilização do app — backup/restauração à prova de falhas, CSVs consistentes e CI verde.

- **Merged:** PRs #12 (coroutines-test bump), #17 (`.env`/segredos fora do controle de versão),
  #19, #21, #22, #23, #25, #26, #27, #28, #29, #30 (proteção de restauração, integridade de
  backup, JSON aninhado, histórico de preços, restrições/alergias, configurações/lixeira,
  CSV alinhado com lojas, `LocalLifecycleOwner` do lifecycle-compose).
- **Verificações:** CI verde em todos os merges (runs até #61: testes + lint + build, success);
  suíte local `./gradlew :app:testDebugUnitTest` executada nas sessões.
- **Estado GitHub:** sem issues abertas, sem PRs abertos; release v1.6.0 publicada com APKs.
- **Conhecido/pendente:** CHANGELOG acumula as correções de segurança em “[Não lançado]”;
  README com referências de release desatualizadas (v1.4.2/v1.5.0 em vez de v1.6.0).

---

## 2026-11-06 — Sessão atual

**Foco escolhido:** (1) corrigir referências desatualizadas de release/testes no README —
defeito documental visível ao usuário; (2) avaliar publicação do acúmulo de correções de
segurança de “[Não lançado]” como nova release, seguindo o workflow de tag `v*` do repo.

**Mudanças:**
- `README.md`: badge de release v1.5.0 → v1.6.0; seção Download aponta v1.6.0 com tamanhos
  reais dos APKs; contagens de testes 257 → 269 (Build from source e seção CI sem número
  fixo para não desatualizar de novo); removida a frase obsoleta sobre variantes `debug`
  anexadas (v1.5.0+ publica apenas APKs de release).
- `WORK_LOG.md`: criado (este arquivo).

**Verificações:** baseline local na árvore compartilhada abortado (outra sessão trabalhando
nos mesmos arquivos); verificação real da suíte feita depois, no commit exato da tag — ver
abaixo. CI do repositório rodou em cada PR (verde nos dois).

**Estado GitHub:** PR #32 (`xiosto/readme-release-refresh`) aberto e **mergeado** com CI verde.

**Desdobramento da mesma sessão — publicação da release:**
- O main avançou no meio do caminho (merge do #31, Busca na Lixeira); a release preparada
  como v1.6.1 virou **v1.7.0** (SemVer: funcionalidade nova pede minor), `versionCode 11`.
- PR #33 (v1.6.1) fechado; rebase com push reescrito foi recusado pelas regras do repo →
  substituído por branch novo `xiosto/release-v1.7.0` no **PR #34**, mergeado com CI verde.
- Tag anotada `v1.7.0` em origin/main; release criada com notas pt-BR ("Comprix v1.7.0 —
  backup blindado e busca na lixeira").
- Workflow de Release (run 3): **verde** (testes no gate + lint vital + `assembleRelease`).
  **Porém** os segredos `KEYSTORE_*` não estão configurados ⇒ o passo de publicar APKs é
  pulado por design (a release pública nunca recebe APK não assinado); os **5 APKs não
  assinados** estão no artifact `apks-release-nao-assinados-v1.7.0` (20.6 / 15.0 / 64.0 /
  22.3 / 22.1 MB). O corpo da release documenta o status e o caminho para anexar.
- Verificação local no commit exato da tag (`--rerun`): **317 testes**; 1 falha transiente
  no teste de orçamento de performance do parser (1.048 ms vs 1.0 ms) num sandbox de 2 CPUs
  sob carga de outro processo — reexecução isolada da classe: **48/48 verdes**. Não é
  regressão; o teste documenta folga de 3× para runners ruidosos.

**Documentação pós-release:** PR com README (badge v1.7.0, testes 317, tabela Download
v1.7.0) e a atualização deste log.

**Próximas tarefas úteis (ordem):**
1. Anexar APKs assinados à release v1.7.0: configurar segredos `KEYSTORE_BASE64`,
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` e re rodar o workflow
   (`workflow_dispatch`), ou baixar o artifact e assinar com `apksigner` (procedimento da
   v1.5.0) — exige a chave privada do mantenedor, fora do alcance do agente.
2. Atualizar a linha "Atual (v1.6.0, versionCode 10)" do `CHECKLIST_QA.md` para
   v1.7.0 / versionCode 11.
3. Próxima melhoria de UX: busca na tela de Histórico, na mesma receita da Lixeira
   (`FiltroDaLixeira` generaliza) — sugerido pela sessão da Lixeira.
4. O branch remoto `xiosto/release-v1.6.1` ficou órfão (não deletável pelas regras do repo,
   aponta para commit não mergeado); ignorável, mas vale saber que existe.

---

## 2026-11-06 — Sessão 2 (agente autônomo): busca na Lixeira + contagem regressiva

**Estado na chegada.** main verde (CI: testes + lint + build em todos os passos), 0 issues
e 0 PRs abertos, árvore limpa. "Não lançado" acumulava a robustez de backup dos merges
anteriores (PRs #17–#30). Nenhum defeito pendente → iteração de melhoria.

**Foco escolhido.** A Lixeira guarda tudo por 30 dias, mas achar um registro dependia de
rolar chip por chip, e o único aviso de prazo era a data exata. Uma melhoria pequena,
completa e verificável: **achar e agir na lixeira**.

**Mudanças (PR #31, mergeado).**
- `FiltroDaLixeira.kt` (novo, lógica pura): casamento sem acento/maiúscula sobre título +
  detalhe (mesmo critério amigável do catálogo), contagem regressiva em dias de calendário
  e rótulo/tom do selo de urgência.
- `LixeiraViewModel`: `busca` + `definirBusca`; `registrosVisiveis` combina categoria E busca.
- `LixeiraScreen`: campo "Buscar na lixeira" (padrão do catálogo, com dica), estado vazio
  que explica "nada casou" com botão de limpar, botões "Esvaziar" escondidos durante a
  busca (atingem a categoria inteira, não o recorte na tela) e selo de prazo nos cartões a
  ≤7 dias do fim ("some hoje" / "some amanhã" / "some em N dias"; vermelho ≤3, âmbar 4–7).
  A data completa permanece em todos os cartões.
- `LixeiraBuscaTest.kt`: 16 testes JVM novos; entradas de CHANGELOG em "Não lançado".

**Verificação.** `./gradlew testDebugUnitTest` → **317 testes, 0 falhas** (eram 301);
`./gradlew lintDebug` → verde; `./gradlew assembleDebug` → verde. CI do PR #31 verde no
runner antes do merge. Um dos novos testes nasceu errado (assertava "CAFE" contra um
registro sem "café") — corrigido o teste, não o código.

**Coordenação entre sessões.** Uma sessão paralela trabalhou no mesmo clone durante esta
iteração (worktree `relwork/`): o commit 77f3b3d (README v1.6.0 + criação deste arquivo)
chegou ao main via o merge do PR #31; o PR #32 dela foi aberto e fechado por ela mesma; o
PR #33 (release) dela conflitou com o merge do #31 e está sendo refeito por ela como
**v1.7.0** (versionCode 11) — decisão correta, "Não lançado" tem "Adicionado", o semver do
repo pede bump minor. Esta sessão não tocou no PR #33, no branch dela nem na tag.

**Próximo passo útil.** (1) Concluir a v1.7.0 — merge do PR #33 + tag `v1.7.0`; o workflow
de release publica os APKs assinados sozinho (com a sessão que o está refazendo). (2) Depois
da release: badge e tabela de Download do README apontam v1.6.0 → atualizar para v1.7.0, e
a linha "Atual (v1.6.0, versionCode 10)" do CHECKLIST_QA. (3) Próxima melhoria de UX:
busca na tela de Histórico, na mesma receita da Lixeira (`FiltroDaLixeira` generaliza).

---

## 2026-11-06 — Sessão 2, iteração 2 (agente autônomo): busca no Histórico + v1.7.0

**Contexto.** A sessão paralela fechou o ciclo da release: PR #34 mergeado (03:34) e tag
`v1.7.0` empurrada — workflow de release verde (testes + `assembleRelease`), release
"Comprix v1.7.0 — backup blindado e busca na lixeira" publicada. **Sem segredos de
assinatura no repo, os APKs seguiram o caminho desenhado**: artifact
`apks-release-nao-assinados-v1.7.0` (78,5 MB, retenção 14 dias) para o mantenedor assinar
com `apksigner` e anexar — a release pública nunca recebe APK não assinado
(`SECURITY.md`, rotação v1.4.2). CI no main verde em todos os pontos.

**Foco escolhido.** A Lixeira acabara de ganhar busca amigável (v1.7.0); o Histórico é a
outra lista que cresce sem parar — achar "aquela compra no Mercado X" exigia rolar meses.

**Mudanças (PR #35, mergeado).**
- `HistoricoScreen`: campo "Buscar compras" na seção "Compras realizadas" (mesma receita
  da Lixeira: cada palavra digitada precisa aparecer no nome da lista ou nas lojas, sem
  acento e sem maiúscula); busca sem resultado ganha cartão próprio com botão de limpar.
- `HistoricoViewModel`: `consulta`/`definirConsulta` + `comprasVisiveis`. As analíticas,
  os números do topo e a variação mensal seguem sobre TODAS as compras — a busca afina a
  lista, nunca reescreve a estatística.
- `TextoUtil.correspondeBusca`: o casamento virou utilitário puro compartilhado;
  `FiltroDaLixeira.corresponde` passa a delegar (16 testes da Lixeira guardam o
  comportamento). 4 testes novos cobrem o utilitário.

**Verificação.** `./gradlew testDebugUnitTest` → **321 testes, 0 falhas** (eram 317);
`./gradlew lintDebug` e `./gradlew assembleDebug` → verdes. Nota de honestidade: uma
execução local falhou de forma transitória e não reproduzível; a verificação final foi uma
re-execução completa (`--rerun-tasks --no-build-cache`) verde, e o CI do PR #35 confirmou
no runner. CI do main verde após o merge.

**Coordenação.** Nenhuma colisão com a sessão da release: arquivos disjuntos, CHANGELOG
adicionou ao "Não lançado" recém-aberto pela v1.7.0.

**Estado do GitHub.** main em CI verde; 0 issues; PRs #31, #35 (esta sessão) e #34
(sessão paralela) mergeados; release v1.7.0 publicada (APKs aguardando assinatura do
mantenedor, pelo design de segurança do repo).

**Próximo passo útil.** (1) O mantenedor anexar os APKs assinados na release v1.7.0 (o
artifact `apks-release-nao-assinados-v1.7.0` expira em 14 dias). Nota: o README e o
CHECKLIST_QA **já apontam v1.7.0** — o PR #36 da sessão paralela mergeou momentos após
este texto ser escrito; o que segue faltando são os próprios APKs na release, não a
documentação. (2) Melhorias futuras de UX: busca na exploração do catálogo por sinônimo
(coluna `sinonimos` já existe), ou filtro por loja no Histórico como atalho da busca.
(3) Revisitar a contagem de testes citada no QA na próxima release (317 no v1.7.0; o
"Não lançado" já soma +4).

---

## 2026-11-06 — Sessão 1, acréscimo: aviso automático de release sem APKs (PR #37)

**Foco.** Defeito de operação identificado ao publicar a v1.7.0: sem os segredos
`KEYSTORE_*`, o workflow de release termina **verde** e a release fica **sem APKs** sem
qualquer sinalização do próprio workflow (o aviso no corpo da v1.7.0 foi manual).

**Mudança (PR #37, mergeado).** Novo passo no `release.yml`, rodando só quando
`ASSINADO != true`: escreve o aviso no **resumo do job** (sempre) e, em runs de tag,
anexa um bloquote ao **corpo da release** com o nome do artifact e os dois caminhos de
resolução (configurar os 4 segredos e re rodar, ou assinar com `apksigner`). Idempotente:
`grep` no corpo antes de editar — re runs não duplicam. Caminho assinado inalterado.

**Verificação.** YAML parseado; script extraído com `bash -n`; execução simulada com `gh`
stub cobrindo os dois caminhos (anexar e "já presente — nada a fazer"); CI do PR verde.

**Estado.** 0 issues, 0 PRs abertos; main em CI verde; release v1.7.0 publicada (APKs
aguardando o mantenedor — artifact expira em 14 dias).
