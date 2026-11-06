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

## 2026-11-06 — Sessão 2, iteração 4: contagem de testes verificada e sincronizada (PR #40)

**Foco.** Próximo passo (3) deixado pela iteração do Histórico: os números de teste
citados no README e no CHECKLIST_QA estavam congelados na v1.7.0 (317) enquanto o
"Não lançado" já somava +17.

**Verificação primeiro.** `./gradlew testDebugUnitTest` → **334 testes · 0 falhas ·
0 pulados** (contagem lida dos XMLs JUnit, não estimada). Aritmética confere:
317 (v1.7.0) + 4 (`TextoUtilTest`, PR #35) + 13 (`FiltroDeHistoricoTest`, PR #39) = 334.

**Mudança (PR #40, mergeado; só documentação, zero código).**
- README: badge `unit_tests-334_passing` e comentário do comando (`# 334 JVM unit tests`).
- CHECKLIST_QA: as 3 citações de "317" passam a **334**, sempre mantendo o histórico
  explícito ("a v1.7.0 tinha 317") para o documento não apagar a própria linhagem.

**Estado do GitHub.** main em CI verde; 0 issues além da #38 (ação do mantenedor:
anexar APKs assinados à v1.7.0 — requer os segredos `KEYSTORE_*`); 0 PRs abertos.

**Nota de auditoria.** A sugestão antiga "busca por sinônimo na exploração do catálogo"
foi checada neste ciclo e JÁ ESTÁ implementada desde a baseline v1.4.0
(`CatalogoSemente.buscar` tem 7 níveis de prioridade cobrindo sinônimos, com testes);
a anotação do work log estava obsoleta e cai fora da lista de pendências.

**Próximo passo útil.** (1) Mantenedor anexar os APKs assinados na release v1.7.0
(issue #38; artifact expira em ~14 dias). (2) Exploração do catálogo ainda lista só o
acervo de fábrica (1.714 itens) — incluir os produtos locais do Room como seção
"seus produtos" na mesma busca seria a próxima melhoria funcional de peso.

## 2026-11-06 — Sessão 2, iteração 5: aviso de teto da busca no catálogo (PR #41, aguardando CI)

**Foco.** Última iteração da janela: a busca da exploração corta silenciosamente em 120
resultados; o usuário lia "120 produtos" como se fosse tudo.

**Mudança (PR #41 — ABERTO, não mergeado nesta janela).**
- `EstadoDoCatalogo.buscaLimitada`: verdadeiro quando a busca bruta atinge o
  `LIMITE_DA_BUSCA` e nenhum filtro de setor está ativo (o rol exibido é a busca inteira;
  com setor ativo a contagem já é recorte, aviso não ajuda).
- Legenda: "120 produtos — teto da busca; refine o termo para afinar o resultado".
- Entrada no CHANGELOG ("Não lançado" → Refinado).

**Verificação local.** Compilação e suíte: 334 testes; o único reprovado é o gate de
tempo `ParserDeTextoLivreTest "parse cabe folgado no orcamento de 16 ms"` —
**REPRODUZÍVEL NESTE SANDBOX** (1,061 e 1,174 ms contra orçamento local de 1,0 ms):
máquina compartilhada mais lenta que a de calibração; o teste só tem folga para
`CI=true` (3×) e JaCoCo (3×). NÃO é regressão — parser intocado, e no CI o limite é 3×.
Ação sugerida ao próximo ciclo: medir steady-state (lote de aquecimento + mínimo de
vários lotes, prática padrão de microbenchmark) em vez de média única fria — mata o
ruído sem afrouxar o gate.

**Estado do GitHub no fechamento.** main em CI verde; issue #38 (mantenedor) a única
aberta; **PR #41 aguardando CI no GitHub** — o próximo ciclo deve conferir o resultado
e mergeá-lo se verde (ou corrigir se vermelho) ANTES de escolher novo foco.

## 2026-11-06 — Sessão 3, iteração 1: PR #41 mergeado; gate do parser estabilizado (PR #42)

**Abertura.** Janela nova de 2h iniciada após a reciclagem do sandbox — que DESTRUÍ o
ambiente local: `.env` regenerado pelo template (token do Comprix perdido do arquivo;
restaurado a partir das instruções originais do usuário — segue LOCAL ONLY, nunca
commitado), Android SDK `/home/z/android-sdk` apagado (reinstalado: cmdline-tools +
platform 35 + build-tools 35.0.0) e cache de artefatos `~/.gradle/caches/modules-2` ido.

**Obstáculos de rede (solução local, nada commitado).** O IP compartilhado do sandbox
recebe **HTTP 429 do CDN do Maven Central** (curl e Gradle JVM). Contornos:
1. `~/.gradle/init.d/sandbox-mirror.gradle` troca a URL do `mavenCentral()` pelo espelho
   Huawei Cloud (200, 61 ms) em todos os níveis — sem tocar no repo;
2. `~/.gradle/gradle.properties` (fora do repo): IPv4 preferido + retries/backoff;
3. SDKs do Robolectric pré-baixados do espelho para `~/.m2` (SDK 9 e SDK 15; os 20
   reprovos do `GerenciadorDeBackupTest` eram só "artifact não baixável").

**PR #41 (teto da busca no catálogo)**: CI verde no GitHub, **mergeado** no início da
sessão. main em verde.

**PR #42 (gate de desempenho do parser), mergeado.** Diagnóstico do falso reprovado:
a média de UMA corrida (300 repetições) mistura custo real com picos de escalonador/GC
— o host que reprovava (1,06–1,174 ms) mede **0,497–0,700 ms** com o mínimo de 5 lotes
de 60 repetições, SEM tocar no parser. Correção = qualidade de medida, não afrouxamento:
orçamento local 1,0 ms INTACTO, fatores CI/JaCoCo inalterados, regressões de 5× seguem
presas em qualquer ambiente. Verificação: suíte local completa **334/334, 0 falhas** no
host que antes reprovava. CI do PR verde; main verde após o merge.

**Estado do GitHub.** main em CI verde; 0 issues além da #38 (mantenedor); 0 PRs abertos;
CHANGELOG "Não lançado" com as entradas de #41 e #42.

**Próximo passo útil.** (1) A infra local do sandbox agora está reprodutível (init
script + espelho + SDKs em ~/.m2) — anotado aqui para a próxima sessão não refazer a
descoberta. (2) Melhoria funcional pendente de peso: produtos locais do Room na busca
da exploração do catálogo ("seus produtos"). (3) Issue #38 segue com o mantenedor
(artifact dos APKs expira em ~14 dias).

## 2026-11-06 — Sessão 3, iteração 2: seus produtos na busca do catálogo (PR #43, mergeado)

**Foco.** A melhoria funcional pendente de maior peso das duas últimas iterações: a
exploração do catálogo listava só o acervo de fábrica (1.714 itens) — produto cadastrado
pelo próprio usuário era invisível na busca.

**Mudança (PR #43, mergeado).**
- Seção própria **"Seus produtos"** acima do acervo quando há consulta digitada e
  NENHUM filtro de setor (regra documentada no código: o setor filtra o acervo, não o
  acervo pessoal). Teto de 12, ordenação por nome normalizado, contagem do topo soma
  acervo + locais, estado "nada encontrado" só quando nenhum dos dois lados casa.
- Mesmo critério amigável das outras buscas (`TextoUtil.correspondeBusca`: cada palavra
  no nome, sem acento/caixa — "queijo faz" acha "Queijo da fazenda").
- Dica de sinônimos herdada do acervo ("Mexerica" → "também: Tangerina") via
  `CatalogoViewModel.sinonimosDoProduto` (função pura `sinonimosDe`, já existente).
- Adicionar passa por `encontrarOuCriar` — nunca duplica produto local existente.
- Lógica pura isolada: `FiltroDeProdutosLocais` (mesma receita de `FiltroDeHistorico`/
  `FiltroDaLixeira`) com 6 testes próprios.

**Verificação.** `./gradlew testDebugUnitTest` local → **340 testes · 0 falhas · 0
pulados** (334 + 6). Nota de honestidade: 1º run do teste novo reprovou porque a
EXPECTATIVA do teste estava errada (ordem alfabética: "mineral" < "tonica") — a
implementação estava correta; expectativa corrigida e re-executado completo, verde.
CI do PR verde em ~3,5 min; main verde após o merge.

**Estado do GitHub.** main em CI verde; 0 issues além da #38 (mantenedor); 0 PRs
abertos; CHANGELOG "Não lançado → Adicionado" com a entrada.

**Próximo passo útil.** (1) Issue #38 segue com o mantenedor (APKs da v1.7.0; artifact
expira ~14 dias). (2) Candidatas: contagem de testes citada no QA volta a divergir
(340 agora) — atualizar na próxima release; filtro por setor também para "Seus
produtos" se o feedback pedir; reler CHECKLIST_QA seção 0 na próxima tag.

## 2026-11-06 — Sessão 3, iteração 3: docs ressincronizados (PR #44, mergeado) e varredura de segurança

**Foco.** Fechamento da janela: o badge do README e as citações do CHECKLIST_QA diziam
334 testes; o real do main é 340 (+6 do PR #43).

**Mudança (PR #44, mergeado; só documentação).** Badge `unit_tests-340_passing`,
comentário do README e as 3 citações do CHECKLIST_QA atualizadas com o número
verificado, mantendo o histórico ("v1.7.0 tinha 317") explícito.

**Verificação.** Suíte local: **340 testes · 0 falhas · 0 pulados** (XMLs JUnit). CI do
PR verde (~45 s); main verde após o merge.

**Varredura de segurança.** Dependabot alerts: **desativados neste repositório** (nada a
fazer via API); 0 issues acionáveis; 0 PRs abertos antes deste.

**Estado do GitHub.** main em CI verde; 0 PRs abertos; issue #38 (mantenedor) a única
aberta.

**Próximo passo útil.** (1) Issue #38 segue com o mantenedor (APKs da v1.7.0; artifact
expira ~14 dias). (2) O número de testes agora churna a cada iteração — na PRÓXIMA
RELEASE, congelar a contagem na nota e evitar divergência citando "verde na suíte"
nas entradas intermediárias. (3) Candidatas de UX: filtro por setor para "Seus
produtos"; busca por sinônimo também no campo do Histórico.

## 2026-11-06 — Sessão 3, acréscimo: dica da busca menciona "Seus produtos" (PR #45, mergeado)

Descobribilidade do PR #43: a dica do campo de busca da exploração descrevia só o
acervo de fábrica; agora avisa que "a busca também olha os seus produtos". Mudança de
string única; `compileDebugKotlin` verde localmente e CI do PR verde (testes + lint +
build) antes do merge. main verde.

## 2026-11-06 — Sessão 3, encerramento: varredura de estado + desenho da próxima iteração

**Foco.** Janela no fim (~10 min): sem iteração de código nesta janela — em vez de
forçar uma feature apressada, verificar o estado e deixar a próxima iteração pronta.

**Verificação de estado.** main CI verde (dc96d60, d5e6e00: success); 0 PRs abertos;
única issue aberta é a #38 (mantenedor, APKs da v1.7.0 — nada acionável pelo agente).
Suíte do main: 340 testes (PRs #43/#44/#45 mergeados nesta sessão).

**Correção de backlog.** A nota "produtos locais do Room na busca" que ainda aparecia
como pendente em iterações anteriores estava DESATUALIZADA: já entregue no PR #43
(seção "Seus produtos") + descobribilidade no PR #45. Removida da lista de pendências.

**Desenho pronto para a próxima sessão — busca por sinônimo no Histórico.**
Achado de avaliação: `HistoricoViewModel` NÃO injeta `CatalogoRepositorio`; a busca
usa `FiltroDeHistorico.comprasVisiveis` (pura) com `TextoUtil.correspondeBusca` sobre
`nomeLista + descricaoEstabelecimento`. Plano:
(1) passar ao `comprasVisiveis` um parâmetro opcional `candidatos: ((String) -> List<String>)? = null`
(retrocompatível com os testes existentes);
(2) o ViewModel injeta `CatalogoRepositorio` (ou lambda `sinonimosDe`) e expande a
consulta: conjunto = {consulta} ∪ {sinônimos da consulta inteira normalizada} — casar
se QUALQUER candidato casa (expansão por palavra inteira, não por palavra solta);
(3) `sinonimosDe` já é bidirecional (`itemPorNome` + `itemPorSinonimo`) — "tangerina"
acha lista "Mexerica" e vice-versa;
(4) casos de teste em `FiltroDeHistoricoTest`: sinônimo direto, inverso, consulta
sem sinônimo (comportamento atual), candidatos nulos (regressão zero).

**Estado do GitHub.** main verde; 0 PRs; #38 a única issue aberta.

**Próximo passo útil.** (1) Implementar o desenho acima (iteração 1 da sessão 4).
(2) #38 segue com o mantenedor. (3) Congelar contagem de testes só na próxima release.

---

## 2026-11-06 — Sessão 4, iteração 1: busca por sinônimo no Histórico (PR #46, mergeado)

**Foco.** O desenho deixado pronto no encerramento da sessão 3, executado como
especificado: a busca de compras do Histórico agora entende os sinônimos do acervo de
fábrica. "mexerica" encontra a lista "Tangerina para o suco" — e "tangerina" encontra
"Mexerica" — porque a resolução do acervo (`sinonimosDe`: `itemPorNome` +
`itemPorSinonimo`) é bidirecional.

**Mudança (PR #46, mergeado).**
- `FiltroDeHistorico.comprasVisiveis` ganha `candidatos: ((String) -> List<String>)? =
  null`, retrocompatível: a compra passa se a consulta direta OU qualquer alternativa
  casar (o mesmo `TextoUtil.correspondeBusca`). Alternativas calculadas UMA vez por
  chamada, não por compra.
- Duas regras deliberadas, documentadas no KDoc e presas por teste: expansão pela
  **consulta inteira** ("suco de mexerica" não expande palavra solta — o resultado
  cresceria em silêncio) e `candidatos = null` = **comportamento de antes** (regressão
  zero para quem não injeta o acervo).
- `HistoricoViewModel` injeta `CatalogoRepositorio` (3º parâmetro do construtor) e passa
  `catalogoRepositorio::sinonimosDe` como lambda — função pura, em memória, sem tocar no
  Room. DI atualizada no `Navegacao`.
- Dica do campo de busca cita o novo alcance ("entende sinônimos do catálogo:
  'mexerica' acha 'Tangerina'") — descobribilidade, mesmo critério do PR #45.
- CHANGELOG "Não lançado → Refinado" com a entrada.

**Verificação.** `./gradlew :app:compileDebugUnitTestKotlin` → verde.
`./gradlew :app:testDebugUnitTest` → **346 testes · 0 falhas · 0 pulados** (340 + 6
novos em `FiltroDeHistoricoTest`, 19 no arquivo: sinônimo direto, inverso, consulta sem
sinônimo, candidatos nulos, frase inteira não expande, E com loja). Contagem lida dos
XMLs JUnit, não estimada. `./gradlew :app:lintDebug :app:assembleDebug` → verdes.
CI do PR verde (~3 min); main verde após o merge (414a68ee, ~4,5 min).

**Coordenação.** Uma nota de ferramenta: o corpo do PR precisa de JSON via arquivo
(`python3 json.dumps` → `curl -d @file`) — o inline com backticks do Markdown quebra no
bash (interpretados como command substitution). Mesma receita dos PRs anteriores.
Contagem de testes nos docs NÃO foi tocada (decisão da sessão 3: congelar só na
próxima release; 346 é volátil).

**Estado do GitHub.** main em CI verde (414a68ee); 0 PRs abertos; issue #38 a única
aberta (mantenedor). Branch remota `historico/busca-sinonimo` ficou no repo (regra do
repo impede exclusão — já documentado na sessão 2).

**Próximo passo útil.** (1) #38 segue com o mantenedor (APKs da v1.7.0). (2) Candidatas
de UX: filtro por setor para "Seus produtos" (PR #43); reler o CHECKLIST_QA seção 0 na
próxima tag. (3) Na próxima release: congelar a contagem (agora 346) e sincronizar docs.

---

## 2026-11-06 — Sessão 4, iteração 2: "Seus produtos" também entende sinônimos (PR #47, mergeado)

**Foco.** Achado de avaliação na sequência: a busca do acervo é sensível a sinônimos
desde a v1.4.0, mas a seção "Seus produtos" (PR #43) casava só a consulta direta —
digitar "tangerina" não achava "Mexerica da feira" gravado pelo usuário. Inconsistência
de conserto barato agora que o PR #46 estabeleceu a receita.

**Mudança (PR #47, mergeado).**
- `FiltroDeProdutosLocais.filtrar` ganha `candidatos: ((String) -> List<String>)? =
  null` — MESMA receita do PR #46: produto passa se a consulta direta OU qualquer
  alternativa casar; expansão pela consulta inteira; `null` = comportamento de antes.
- `CatalogoViewModel.calcular` passa `catalogoRepositorio::sinonimosDe`. A seção segue
  só existindo com consulta digitada e sem filtro de setor; teto 12 e ordenação intactos.
- Dica do campo do catálogo agora diz "melao acha Melão, mexerica acha Tangerina".
- CHANGELOG "Não lançado → Refinado" com a entrada.

**Verificação.** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
numa passada → **351 testes · 0 falhas · 0 pulados** (346 + 5 novos em
`FiltroDeProdutosLocaisTest`: sinônimo direto, inverso, candidatos nulos, frase inteira
não expande, busca direta segue valendo). Contagem dos XMLs JUnit. CI do PR verde
(~4 min); main verde após o merge (900e0a38).

**Coordenação.** Arquivos disjuntos do PR #46 (o filtro de catálogo vs o de histórico);
zero colisão. A receita agora está em DOIS filtros puros com as mesmas regras —
próximo candidato natural, se um dia a busca da Lixeira pedir sinônimos, é copiar o
padrão; os testes de referência já existem nos dois lados.

**Estado do GitHub.** main em CI verde (900e0a38); 0 PRs abertos; issue #38 a única
aberta (mantenedor).

**Próximo passo útil.** (1) #38 segue com o mantenedor. (2) Candidatas: filtro por setor
para "Seus produtos" (só se feedback pedir — decisão documentada no PR #43); na próxima
release congelar a contagem (agora 351) e sincronizar docs/badge. (3) Varredura de
defeitos: rodar o lint completo com todos os avisos e avaliar o que é acionável.

---

## 2026-11-06 — Sessão 4, iteração 3: varredura de lint + higiene de recomposição (PR #48, mergeado)

**Foco.** Varredura de defeitos prometida na iteração anterior: TODO/FIXME no código
(nenhum real — os matches eram "TODOS" em português) e o relatório completo do lint
(50 warnings + 3 infos). Classificação por acionabilidade real:

**Defeitos reais consertados (PR #48, mergeado; zero mudança de comportamento).**
- `BarraDeRolagem` lia `estado.layoutInfo` na COMPOSIÇÃO — muda a cada quadro de
  rolagem, então a barra recomporia por frame. Toda a leitura migrou para o draw do
  Canvas (casos "nada a desenhar" não pintam). Aviso `FrequentlyChangedStateReadInComposition` sumiu.
- `ChaveComprix`: cursor animado usava `offset(x: Dp)` (recompõe por frame) →
  sobrecarga por lambda com `IntOffset`/`roundToPx` (só re-layout).
  Aviso `UseOfNonLambdaOffsetOverload` sumiu.
- `EditorDeItem`/`ListaScreen`: `mutableLongStateOf`/`mutableFloatStateOf` nos 3 estados
  primitivos (categoriaId, gramas, deslocamentoDoArrasto) — sem boxing por escrita.
  Avisos `AutoboxingStateCreation` sumiram.

**Avaliados e DEFERIDOS, com motivo (registrado no corpo do PR #48).**
- `GradleDependency` ×13: bumps grandes (CameraX 1.3→1.6, Navigation 2.8→2.10,
  Lifecycle 2.8→2.11) sem teste em dispositivo — bump cego não cabe num app em
  estabilização.
- `StaticFieldLeak` ×4: padrão deliberado (`ServiceLocator`/`Feedback` guardam o
  contexto de APLICAÇÃO, que vive tanto quanto o processo — falso positivo prático).
- `UnusedResources` (12 cores + 8 strings desc_*), `HardcodedText` (widget),
  `RedundantLabel`, `Overdraw`, `IconLauncherShape`, `ModifierParameter`: higiene/design
  com decisão de produto — candidatas a PRs próprios.

**Verificação.** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
numa passada → **351 testes · 0 falhas**; lint 50 warnings + 3 infos → **48 warnings**
(5 avisos alvo eliminados). CI do PR verde (~3,5 min); main verde após o merge
(d809f951).

**Estado do GitHub.** main em CI verde (d809f951); 0 PRs abertos; issue #38 a única
aberta (mantenedor).

**Próximo passo útil.** (1) #38 segue com o mantenedor. (2) Próxima varredura: higiene
de recursos (strings desc_* e cores sem uso — conferir referências antes de apagar),
strings do widget para @string e label redundante do manifesto. (3) Na próxima release:
congelar contagem (351) e sincronizar docs/badge.

---

## 2026-11-06 — Sessão 4, iteração 4: higiene de recursos — widget e manifesto (PR #49, mergeado)

**Foco.** A segunda leva da varredura de lint, executada com verificação de referências
ANTES de qualquer exclusão (lição de método: lint aponta "sem uso", mas a intenção
documentada decide).

**Mudança (PR #49, mergeado; zero mudança de comportamento).**
- `lista_widget.xml`: os dois textos hardcoded ("Comprix", "0 de 0 comprados") eram
  placeholders de preview — confirmado que o `ListaWidgetProvider` sobrescreve os dois
  campos com `setTextViewText` antes de exibir. Agora em `@string` (`app_name` e a nova
  `widget_contagem_padrao`, com comentário explicando o papel de preview).
  Avisos `HardcodedText` ×2 sumiram.
- `AndroidManifest`: `android:label` da `MainActivity` duplicava o do `<application>`
  (ambos `@string/app_name`); a activity herda — removido. Aviso `RedundantLabel` sumiu.
- **`UnusedResources` avaliado e MANTIDO** (13 cores + 8 strings desc_*): o cabeçalho do
  `colors.xml` documenta que os tokens XML espelham de propósito a paleta Compose
  (alinhamento + evitar a "piscada" na abertura) — apagar contradiria a decisão
  documentada. As `desc_*` são descrições de acessibilidade com uso futuro imediato.
  Registrado no corpo do PR para o próximo agente não "consertar" de volta.

**Verificação.** `./gradlew :app:lintDebug :app:assembleDebug :app:testDebugUnitTest`
→ verdes; **351 testes · 0 falhas**; lint **48 → 45 avisos**. CI do PR verde (~5 min);
main verde após o merge (895c510b).

**Acompanhamento do lint na sessão.** 50 warnings + 3 infos → **45 warnings**
(8 eliminados: 5 no PR #48, 3 no #49). Os 45 restantes são os deferidos com motivo
(deps grandes, StaticFieldLeak falso-positivo, IconLauncherShape, Overdraw,
ModifierParameter) — nada acionável sem decisão de produto ou teste em dispositivo.

**Estado do GitHub.** main em CI verde (895c510b); 0 PRs abertos; issue #38 a única
aberta (mantenedor).

**Próximo passo útil.** (1) #38 segue com o mantenedor (APKs da v1.7.0). (2) Na próxima
release: congelar contagem (351), sincronizar docs/badge e rotular o "Não lançado"
(sinônimos no Histórico + Seus produtos + higiene de recomposição). (3) Candidatas de
UX com decisão de produto: filtro por setor para "Seus produtos" (só com feedback);
favoritos na lista de produtos.

---

## 2026-11-06 — Sessão 4, encerramento: varredura de estado + desenho da release v1.8.0

**Foco.** Janela restante (~40 min): sem iteração nova de código — as candidatas de UX
exigem decisão de produto (feedback) e a release v1.8.0 é um lote maior que não cabe
com folga no relógio. Em vez de uma release apressada (que nasceria sem APKs assinados
e ACRESCENTARIA à dívida já rastreada na #38), verificar o estado e deixar a release
desenhada para a próxima sessão.

**Verificação de estado.** main CI verde (75804be, e todos os 4 merges desta sessão:
414a68ee, 900e0a38, d809f951, 895c510b); 0 PRs abertos; única issue aberta é a #38
(mantenedor). Suíte do main: **351 testes** (PRs #46/#47/#48/#49 mergeados nesta
sessão). Lint: 50 warnings + 3 infos → 45 warnings (8 eliminados).

**Bônus de des-risco da release: `./gradlew :app:assembleRelease` verde** (5 min) —
R8/minify sem erro, 3 APKs não-assinados gerados por ABI splits (arm64-v8a 21,7 MB,
armeabi-v7a 15,7 MB, universal 67,1 MB). O CI só roda `assembleDebug`, então este é o
check que faltava para a v1.8.0. O único warning do manifest merger é o padrão
deliberado `tools:node="remove"` nas permissões INTERNET/NETWORK_STATE/WAKE_LOCK
(critério #1 do CHECKLIST_QA — app 100% modo avião). Nada a consertar.

**Sessão 4 em resumo.** 4 PRs mergeados, todos CI verde antes do merge:
- **#46** — busca do Histórico entende sinônimos do acervo (receita dos `candidatos`).
- **#47** — "Seus produtos" do catálogo também entende sinônimos (mesma receita).
- **#48** — higiene de recomposição (scrollbar desenha sem recompor, offset por lambda,
  estados primitivos).
- **#49** — higiene de recursos (widget @string, label redundante; UnusedResources
  MANTIDO por decisão documentada no colors.xml).

**Desenho pronto — release v1.8.0 (executar numa janela com ≥50 min):**
1. Branch `release/v1.8.0`: `versionCode = 12`, `versionName = "1.8.0"` (hoje linha
   46–47 de `app/build.gradle.kts`).
2. CHANGELOG: "Não lançado" → "[1.8.0] — 2026-11-06" (Adicionado: Seus produtos;
   Refinado: teto da busca, sinônimos no Histórico e em Seus produtos, busca no
   Histórico, filtro por loja; Interno: gate do parser, higiene de recomposição,
   recursos/widget/manifesto).
3. README: badge `unit_tests-351_passing` + contagem no comentário; CHECKLIST_QA
   seção 0: coluna "Atual (v1.8.0, versionCode 12)" com 351 (mantendo o histórico
   "v1.7.0 tinha 317").
4. Verificação local completa (testes + lint + build), PR, CI verde, merge.
5. Tag `v1.8.0` no main pós-merge; GitHub Release via API com as notas do CHANGELOG.
6. **APKs: a release nascerá sem APKs assinados** (mesma dívida da #38 — o keystore
   está fora do repo por design). Anexar o artifact `apks-release-nao-assinados-*` do
   CI se existir e referenciar a #38 nas notas. A issue #38 pede explicitamente APKs
   ASSINADOS; reportar isso na release para não parecer omissão.
7. Pós-release: reexecutar as 5 jornadas do CHECKLIST_QA seção 2 é "a cada release
   maior" — v1.8.0 é minor; registrar a decisão de não reexecutar (sem device).

**Estado do GitHub.** main verde; 0 PRs; #38 a única issue aberta.

**Próximo passo útil.** (1) Executar o desenho da v1.8.0 (iteração 1 da sessão 5).
(2) #38 segue com o mantenedor. (3) Candidatas de UX com decisão de produto ficam na
prateleira: filtro por setor para "Seus produtos"; favoritos na lista de produtos.

---

## 2026-11-06 — Sessão 5, iteração 1: RELEASE v1.8.0 publicada (PR #50, mergeado; tag + release + workflow de release verdes)

**Foco.** O desenho da sessão 4, executado de ponta a ponta com janela folgada.

**Mudança (PR #50, mergeado).**
- `app/build.gradle.kts`: versionCode 11 → **12**, versionName "1.7.0" → **"1.8.0"**.
- CHANGELOG: "Não lançado" → **[1.8.0] — 2026-11-06** (Seus produtos; teto da busca;
  sinônimos no Histórico e em Seus produtos; busca e filtro por loja no Histórico;
  gate do parser; higiene de recomposição e de recursos).
- README: badges `release-v1.8.0` e `unit_tests-351_passing`; seção Download ("v1.0 a
  v1.8.0") e build-from-source com a contagem congelada em 351.
- CHECKLIST_QA seção 0: coluna "Atual (v1.8.0, versionCode 12)" com **351** (v1.7.0
  tinha 317; pós-v1.7.0: +17 busca/filtro, +6 Seus produtos, +11 sinônimos — 6 no
  Histórico, 5 em Seus produtos); critério 10 e "Estado atual (v1.8.0)" alinhados.

**Publicação.**
- PR #50 → CI verde (~2 min) → merge (**c83c1585**); main verde no commit de release.
- **Tag `v1.8.0` + release** criadas via API apontando para c83c1585 — erro de validação
  na 1ª tentativa (sha truncado a 8 dígitos no `target_commitish`; a API exige o sha
  completo), corrigido na 2ª.
- O push da tag disparou o workflow **Release** (run **42191155823**, verde, ~3,5 min):
  gate de testes + `assembleRelease` + artifact `apks-release-nao-assinados-v1.8.0`
  (74 MB, 5 ABIs, expira 2026-11-20) + o aviso canônico de APKs ausentes anexado
  automaticamente ao corpo da release (idempotente, como desenhado no próprio yml).
- Release pública: https://github.com/nadabgfccvd/Comprix/releases/tag/v1.8.0 — notas
  no padrão da v1.7.0 (novidades, qualidade, tabela "Qual APK baixar?", compare link);
  0 assets (APK sem assinatura nunca vai para a release pública).

**Coordenação com a #38.** O artifact da v1.8.0 expira na MESMA data (~2026-11-20) do
da v1.7.0. Comentário publicado na issue (#issuecomment-6681152383) consolidando os
dois artifacts numa tabela, com o link do run e os dois caminhos de assinatura — o
mantedor resolve os dois de uma vez. A issue segue aberta para ele.

**Verificação.** Local antes do PR: 351 testes · 0 falhas (XMLs), lintDebug e
assembleDebug verdes; assembleRelease já verificado na sessão 4. CI do PR verde;
CI do main verde no c83c1585; workflow Release verde no run da tag.

**Decisões registradas.** (1) As 5 jornadas do CHECKLIST_QA (seção 2) NÃO foram
reexecutadas: o critério do próprio documento é "a cada release maior" e v1.8.0 é
minor — sem device/emulador no ambiente, reexecutar só por ritual não adicionaria
evidência. (2) Contagem congelada nos docs em 351; iterar sobre main volta a mudar o
número, que só volta a ser sincronizado na PRÓXIMA release.

**Estado do GitHub.** main em CI verde (c83c1585 + work log); release v1.8.0
publicada; 0 PRs abertos; issues abertas: **#38** (mantenedor, agora com os dois
artifacts consolidados).

**Próximo passo útil.** (1) #38 segue com o mantenedor (2 artifacts, 1 procedimento).
(2) Candidatas de UX com decisão de produto: filtro por setor para "Seus produtos"
(só com feedback); favoritos na lista de produtos. (3) Monitorar o CI do main nas
próximas janelas; varredura de estado a cada check-in.

---

## 2026-11-06 — Sessão 5, iteração 2: Lixeira também entende sinônimos — unificação completa (PR #51, mergeado)

**Foco.** O work log da sessão 4 apontou o candidato natural restante: com a receita
dos `candidatos` consolidada em dois filtros, copiar para a Lixeira fecha a unificação.
Avaliação antes de começar: acervo (sinônimo-aware desde v1.4.0) ✓, "Seus produtos" ✓
(#47), Histórico ✓ (#46), **Lixeira ✗** — a única busca que não falava a língua das
outras. Nota: "favoritos na lista de produtos" continua na prateleira por constraint
 dura (migration nova é proibida no projeto; ProdutoEntity não tem coluna e não pode
ganhar).

**Mudança (PR #51, mergeado).**
- `FiltroDaLixeira.alternativasDe(consulta, candidatos)`: função pura nova — computa
  os termos alternativos da consulta inteira UMA vez por emissão (a regra do #46 de
  não rodar por registro preservada; `corresponde` é por-registro).
- `FiltroDaLixeira.corresponde(registro, consulta, alternativas = emptyList())`:
  parâmetro default retrocompatível — registro passa se a consulta direta OU qualquer
  alternativa casar. Regras deliberadas intactas: expansão pela consulta inteira,
  consulta em branco nem chama os candidatos, `null` = comportamento de antes.
- `LixeiraViewModel` injeta `CatalogoRepositorio` (2º parâmetro) e passa
  `catalogoRepositorio::sinonimosDe`; DI no `Navegacao`; dica do campo cita o alcance.
- CHANGELOG "Não lançado → Refinado" reaberto (pós-v1.8.0).

**Verificação.** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
→ verdes; **355 testes · 0 falhas · 0 pulados** (351 + 4 novos em `LixeiraBuscaTest`:
sinônimo direto, inverso, sem sinônimo/sem candidatos, em branco + frase inteira não
expande). CI do PR verde (~5 min); main verde após o merge (**2554eada**).

**Estado do GitHub.** main em CI verde; 0 PRs abertos; release v1.8.0 publicada;
#38 a única issue aberta (mantenedor, com os 2 artifacts consolidados no comentário
da iteração 1).

**Próximo passo útil.** (1) #38 segue com o mantenedor. (2) Na PRÓXIMA release: a
entrada da Lixeira no "Não lançado" vai junto (congelar contagem em 355+). (3) Sem
outro candidato de unificação — todas as buscas agora compartilham a receita; os 3
filtros puros têm testes de referência com a mesma lambda de sinonimos de teste.

---

## 2026-11-06 — Sessão 5, acréscimo: QA do artifact da v1.8.0 (5 APKs verificados com aapt2)

Download do artifact `apks-release-nao-assinados-v1.8.0` (via API, zip de 74 MB) e
inspeção `aapt2 dump badging` nos 5 APKs — evidência que o CI não produz:

- **versionCode='12' · versionName='1.8.0'** em todos (arm64-v8a, armeabi-v7a,
  universal, x86, x86_64 — 5 ABIs como nas notas da release);
- Permissões: apenas `CAMERA`, `VIBRATE` e a de assinatura
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — **sem INTERNET** (critério #1 do
  CHECKLIST_QA, app 100% modo avião) e sem `ACCESS_NETWORK_STATE`;
- targetSdk 35, compileSdk 35.

O estado da release está íntegro de ponta a ponta: tag no commit certo, workflow
verde, artifact correto, notas e avisos no lugar. Arquivos temporários apagados após
a verificação (nada de build local vai para o repo).

---

## 2026-11-06 — Sessão 5, iteração 3: contagem dos docs ressincronizada 351 → 355 (PR #52, mergeado)

**Foco.** O "próximo passo útil" da iteração 2 alertava para o congelamento da contagem
na próxima release — mas o passo imediato era mais simples: o PR #51 somou +4 testes e
a contagem real do main virou 355, enquanto README e CHECKLIST_QA diziam 351 (exatamente
o desvio que o PR #40 já tinha corrigido uma vez). Avaliação antes de começar: CI do main
verde, 0 PRs abertos, #38 segue com o mantenedor.

**Verificação antes de editar (fonte da verdade).** `./gradlew :app:testDebugUnitTest`
→ verde (UP-TO-DATE, mesmas entradas da corrida completa); contagem lida dos XMLs JUnit
(`app/build/test-results/testDebugUnitTest`): **355 testes · 0 falhas · 0 pulados**.
Aritmética confere: 351 (v1.8.0) + 4 (`LixeiraBuscaTest`) = 355.

**Mudança (PR #52, mergeado — só docs, 2 arquivos, 5 linhas).**
- `README.md`: badge `unit_tests-351_passing` → `355_passing` (com o alt text) e o
  comentário do comando de teste.
- `CHECKLIST_QA.md` (3 referências): seção 0 com a linhagem atualizada ("...somou +11:
  sinônimos na busca do Histórico (+6) e em 'Seus produtos' (+5); já pós-v1.8.0,
  sinônimos na busca da Lixeira somaram +4"); critério 10 ("Atual: 355 testes");
  estado final reetiquetado de "(v1.8.0)" para "(pós-v1.8.0)" — honesto, porque a
  release publicada congela em 351 e o 355 é estado do main.
- História intacta: v1.7.0 = 317 e v1.8.0 = 351 permanecem como estão.

**Verificação pós-edit.** `grep -rn "351" --include="*.md"` fora do WORK_LOG → 0
ocorrências. CI do PR #52 verde (~1 min); main verde após o merge. Diff revisado:
sem segredos, sem código, sem build.

**Estado do GitHub.** main em CI verde; 0 PRs abertos; #38 a única issue aberta
(mantenedor); release v1.8.0 íntegra.

**Próximo passo útil.** (1) #38 segue com o mantenedor (exige keystore). (2) Na PRÓXIMA
release (v1.8.1 ou v1.9.0): a entrada da Lixeira no "Não lançado" vai junto e a
contagem congela em 355+. (3) Sem candidato de feature pendente no radar — todas as
buscas unificadas, release íntegra, docs sincronizados; próximas iterações só com
defeito novo, sugestão externa ou melhoria incremental bem delimitada.

---

## 2026-11-06 — Sessão 5, iteração 4: widget com objeto puro e 10 testes (PR #53, mergeado)

**Foco.** Varredura por lacuna de cobertura em superfície visível achou o candidato:
o **widget da tela inicial** era a única parte do app visível ao usuário sem um único
teste — título do estado vazio, convite ("Abra o app para comecar"), limites da barra
e a linha do estimado eram decididos dentro do `ListaWidgetProvider`, acoplados a
`RemoteViews`. Mesma doutrina de `FiltroDeHistorico`/`FiltroDeProdutosLocais`/
`FiltroDaLixeira`: lógica → objeto puro com testes próprios. Avaliação antes de
começar: main verde no b56789c (iteração 3), 0 PRs, #38 do mantenedor.

**Mudança (PR #53, mergeado).**
- `VisoesDoWidget` (novo, objeto puro): calcula `EstadoDoWidget` (`textoNome`,
  `maximoDaBarra`, `progressoDaBarra`, `textoContagem`, `estimadoVisivel`,
  `textoEstimado`) a partir de `ResumoDeLista?` — JVM pura, sem Android.
- `ListaWidgetProvider.visoes()` só traduz o estado em `RemoteViews` + PendingIntent;
  imports mortos (`Formatadores`, `BigDecimal`) saíram.
- **Nenhuma mudança de comportamento**: cada texto, limite e regra de visibilidade é
  exatamente o de antes (sem lista/nome em branco → "Nenhuma lista aberta" com
  contagem real; total 0 → convite; estimado só com valor positivo, pt-BR).
- Docs no MESMO PR para não recriar o desvio corrigido na iteração 3: contagem
  355 → 365 (README badge + comando, 3 refs do CHECKLIST_QA com a linhagem "+10
  widget") e entrada "Não lançado → Interno" no CHANGELOG.

**Verificação.** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
→ verdes; contagem dos XMLs JUnit: **365 testes · 0 falhas · 0 pulados** (355 + 10
novos em `VisoesDoWidgetTest`: estado vazio ×3, contagem/barra ×2, estimado ×4,
guarda de construção ×1). Aritmética: 355 + 10 = 365. CI do PR verde (~4 min);
main verde após o merge. Diff revisado: sem segredos; `ResumoDeLista`/`ListaDeCompras`
já eram JVM-puros, zero reflexão ou Robolectric necessário.

**Nota de processo (ferramenta).** `MultiEdit` no editor local reporta falha mesmo
quando a primeira edição foi aplicada (execução sequencial, não atômica) — re-ler o
arquivo antes de repetir uma edição evita duplicar mudança.

**Estado do GitHub.** main em CI verde; 0 PRs abertos; #38 a única issue aberta
(mantenedor); release v1.8.0 íntegra e inalterada (351 congelados).

**Próximo passo útil.** (1) #38 segue com o mantenedor. (2) Na próxima release: as
duas entradas do "Não lançado" (Lixeira + widget) vão junto e a contagem congela em
365+. (3) Cobertura de superfícies puras restante: nenhuma conhecida sem testes —
próximas iterações continuam dependentes de defeito novo, sugestão externa ou
melhoria bem delimitada.
