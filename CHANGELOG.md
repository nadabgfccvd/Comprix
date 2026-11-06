# Changelog

Todas as mudanças notáveis do Comprix ficam registradas aqui, com base nas notas de cada [release](https://github.com/nadabgfccvd/Comprix/releases). O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); as versões seguem [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Interno
- **O repositório de listas ganhou teste de ponta a ponta com Room em memória**: `ListaRepositorio` concentra a tela inicial do app — criar, favoritar, finalizar, orçar, duplicar e somar o estimado — e só tinha teste direto para as funções puras do companion (`proximoNomeDeCopia` e `nivelDoOrcamento`, em `ListasTest`/`OrcamentoTest`). 19 testes novos (Robolectric SDK 28, mesmo padrão dos outros repositórios) pinam o que passa pelo banco: criar com nome informado, com `null` ou em branco cai no nome sugerido do mês (`Formatadores.nomeSugeridoDeLista`); renomear apara espaços; excluir **com** Lixeira desvia para a lixeira (registro `LISTAS` com o título) e **sem** ela apaga direto; favoritar marca/desmarca e ignora id inexistente; finalizar grava o momento e reabrir limpa; orçamento grava, sai com `null` e ignora id inexistente; cada item entra com ordem manual de 10 em 10; `listarItens` casa o item com o produto e pula o órfão; o atalho de favoritos soma a quantidade na **primeira** ocorrência do produto (fora da lista devolve `false`); marcar comprado e desmarcar-todos afetam só a lista pedida; `aplicarOrdem` reordena, ignora id inexistente e mapa vazio; **duplicar copia os itens desmarcados e não copia os preços** (preço vencido induziria comparação falsa), numera as cópias sem acumular sufixo ("(cópia)", "(cópia 2)", "(cópia 3)") e o nome digitado entra puro, com colisão ganhando sufixo; duplicar id inexistente devolve `null`; o total estimado soma o **menor preço disponível e positivo** de cada item (zero e indisponível não contam) e devolve zero sem itens ou sem preços; os resumos contam itens/comprados por lista e `resumosFavoritas` filtra as favoritas. Nenhuma mudança em código de produção.

### Interno
- **As preferências e o perfil de restrições ganharam teste de ponta a ponta com Room em memória**: `ConfiguracoesRepositorio` guarda as preferências do app e o perfil em duas tabelas de linha única (id = 1) — e não tinha teste direto. 13 testes novos (Robolectric SDK 28, mesmo padrão dos outros repositórios): em banco recém-criado, sem linha nenhuma, `carregar()` devolve os padrões do domínio (mapeamento de entidade nula); cada ajuste usa o padrão `alterar` — lê, copia, grava — e os cinco ligadores de aparência/feedback **se preservam entre si**; a escala tipográfica cai no degrau de 5 e fica presa entre 100 e 160 (147 → 145, 90 → 100, 200 → 160); lista de nutrientes vazia volta para a seleção padrão; dicas vistas acumulam e `reexibirDicas` limpa; a meta de economia grava e sai com `null`; onboarding e backup registram o momento fixo informado; salvar o perfil **nunca apaga as alergias customizadas** (tabela própria, que entra no `perfil` via combine — pinado com o Flow real); criar restrição própria normaliza as palavras, descarta vazias/duplicadas e ignora nome em branco ou duplicado normalizado; remover com Lixeira manda para a lixeira e sem ela apaga direto. Nenhuma mudança em código de produção.

### Interno
- **Os preços anotados ganharam teste de ponta a ponta com Room em memória**: `PrecoRepositorio` guarda o par item + loja e alimenta o histórico por produto que sobrevive a apagar a lista — e não tinha teste direto. 17 testes novos (Robolectric SDK 28, mesmo padrão dos testes da Lixeira e da ponte parser -> banco): regravar o preço do mesmo item na mesma loja **substitui** (nunca duplica); só preço disponível e maior que zero entra no histórico; o aviso de preço alto considera o mínimo registrado em **qualquer** loja (a boa oferta do concorrente é a referência que ajuda a decidir — regra documentada no próprio repositório, agora pinada), capturado ANTES da gravação: primeiro registro, empate e preço menor não avisam, indisponível não avisa nem entra no histórico, e acima do mínimo a mensagem cita o percentual inteiro (HALF_UP) e o menor valor formatado pt-BR; `marcarIndisponivel` preserva o preço anterior e marca a flag; `garantirEstabelecimento` devolve a existente por nome, vira "Meu mercado" com nome em branco e escolhe a cor da paleta em rodízio; remover estabelecimento com Lixeira manda para a lixeira sem os preços e sem ela apaga direto; `menorPrecoRecente` devolve o mínimo em reais e a série histórica vem do mais recente para o mais antigo. Nenhuma mudança em código de produção.

### Interno
- **A Lixeira ganhou teste de ponta a ponta com Room em memória**: `LixeiraRepositorio` é o contrato de exclusão recuperável — todo "apagar" do app passa por ele — e era exercitado só indiretamente pelo teste do backup. 21 testes novos (Robolectric SDK 28, mesmo padrão da ponte parser -> banco) congelam o comportamento que a documentação do backup depende: enviar lista/loja/produto/compra/alergia grava o registro na lixeira e apaga o original (o preço anotado no item da lista enviada desaparece junto, por CASCADE; os preços da loja saem antes da loja; a lista e o item sobrevivem ao envio da loja); as 14 categorias padrão são imutáveis — só categoria do usuário vai para a lixeira; restaurar recria com **id autogerado** sem tocar no que foi criado depois do envio, recria o produto sumido na primeira categoria e pula o item sem produto reencontrável mantendo a lista; nome de loja em uso ganha o sufixo "(restaurada)" com a cor original; produto ou categoria que já voltaram a existir **falham sem duplicar** e o registro permanece na lixeira; compra com payload mínimo nasce com os valores padrão; a purga de 30 dias é **inclusiva** no limite (`<=` no DAO: o registro que completa exatamente o prazo é apagado); sucesso remove o registro da lixeira, falha o mantém. Nenhuma mudança em código de produção.

### Interno
- **A ponte parser -> banco ganhou teste de ponta a ponta**: `EntradaDeTextoLivre` é o fluxo nº 1 do app ("digitei, entrou na lista") e não tinha teste direto — o parser puro tem a especificação JSON, mas quem carrega a memória, resolve o produto no catálogo e grava na lista é a ponte. 12 testes novos com **Room em memória** (mesmo padrão do teste de backup, Robolectric SDK 28): texto em branco não produz item; cada linha é uma entrada independente (casos exatos da especificação, como `uma duzia de ovos`); adicionar cria o produto no catálogo e o item na lista; digitar o mesmo texto de novo **reusa** o produto existente (nunca duplica); quantidade nula vira 1 com o motivo do parser na observação; item pesável nasce comparando por peso e conteúdo por unidade; lote ignora item sem nome; "manter como está" grava a rejeição sem valor e confirmar grava o valor aceito na tabela `decisoes_parser`; ignorar não aprende nada. Nenhuma mudança em código de produção. Nota registrada: a semeadura das 14 categorias vive no builder de ARQUIVO do banco (`ComprixDatabase.construir`), então o teste em memória semeia pela mesma via manualmente (`DadosIniciais.semear`) — e, com a tabela vazia, um produto novo nasceria com `categoriaId = 0` (fallback do categorizador sem categorias); inofensivo no app real, onde a semeadura é garantida na criação do arquivo.

### Refinado
- **A leitura de rótulo do scanner ganhou contrato próprio**: `LeituraDeRotulo` alimenta a tela de revisão do scanner — quais campos aparecem como "lido" e a confiança média que diz onde olhar com atenção — e não tinha teste direto. 14 testes JVM novos congelam as regras sutis: texto em branco não é campo lido; glúten só conta quando foi determinado (não "INDETERMINADO"); tabela nutricional só conta quando tem conteúdo; preço e quantidade **zero** são leituras legítimas; a ordem dos 11 campos da revisão está congelada; a confiança média usa só os campos lidos **e** com confiança registrada (campo lido sem confiança sai da conta; confiança de campo não lido não entra); e o guia do modo vídeo divide a gravação em 5 fatias iguais com as bordas congeladas (duração inválida fica na primeira etapa, instante fora da gravação é preso na faixa, fatia menor que 1 ms nunca divide por zero). Junto, os mini-contratos que ficavam fora da especificação ponta-a-ponta: `MemoriaDoParser` compara termos sempre em minúsculas e `ItemInterpretado.prontoParaAdicionar` exige nome + confiança que não seja BAIXA (confiança baixa abre o editor em vez de entrar direto na lista). 9 testes. Nenhuma mudança em código de produção.

### Refinado
- **Os mapeadores Room <-> domínio ganharam testes de contrato**: `Mapeadores` é a fronteira onde a "sujeira" de persistência mora — centavos viram `BigDecimal`, epoch vira data, texto com `|` vira lista de enum, tabela nutricional vira JSON — e era o último objeto puro do `data` sem teste direto. 28 testes JVM novos congelam o contrato: dinheiro com arredondamento HALF_EVEN (empate vai para o dígito par — 12,345 vira 1234, não 1235), datas com null preservado, a decisão central de que **enum inválido (backup de versão futura) cai num valor seguro em vez de lançar**, descarte silencioso de nomes desconhecidos nas listas `|`, decimal ilegível virando null (e quantidade ilegível virando 1, nunca exceção), JSON com chaves desconhecidas descartadas e ida-e-volta completa de cada modelo. Nenhuma mudança em código de produção.

### Refinado
- **A busca da Lixeira também entende sinônimos do catálogo**: digitar "mexerica" encontra a lista "Tangerina da feira" que foi para a lixeira — e "tangerina" acha "Mexerica", porque a resolução do acervo é bidirecional. Mesma receita já estabelecida na busca do Histórico e em "Seus produtos": expansão pela consulta inteira, nunca por palavra solta; alternativa computada uma vez por emissão da busca; sem sinônimo, o comportamento de antes vale intacto. Com isto, todas as buscas do app falam a mesma língua. A dica do campo cita o novo alcance.

### Interno
- **O divisor de linhas do parser ganhou testes de contrato próprios**: `DivisorDeLinha` — a porta de entrada do parser de texto livre, que quebra "arroz, feijão; leite" em itens — era exercitado só de ponta a ponta pela especificação do parser. 15 testes JVM novos congelam o contrato direto: proteção do decimal e da fração ("2,5" e "1/2" não são separadores), a abreviação "c/" virando "com", o conectivo `e` que separa itens mas fica preso na quantidade ("um quilo e meio"), os itens colados por espaço depois de um preço (corte conservador, com os dois falso-negativos documentados) e a regra do apelido de animal ("cachorro" é 5 no jogo do bicho E produto de verdade — nunca vira "12 ovos" em silêncio). Nenhuma mudança em código de produção. Observação registrada no work log: `DivisorDeLinha.padraoDe` é código morto (nenhum chamador em produção nem em teste) — fica para o mantenedor decidir entre reviver ou remover.
- **As regras de exibição do widget viram objeto puro com testes próprios**: o widget da tela inicial ("primeira lista aberta") decidia título do estado vazio, convite de contagem, limites da barra e a linha do estimado dentro do provider — decisões visíveis na tela inicial sem um único teste. Agora um objeto puro (`VisoesDoWidget`) calcula o estado e o provider só traduz em `RemoteViews`; 10 testes JVM novos cobrem o estado vazio (widget recém-colocado, nome em branco, lista sem itens), a contagem e a barra (incluindo lista completa) e o estimado (aparece só com valor positivo, formatado pt-BR com separador de milhar). Nenhuma mudança visível: cada texto, limite e regra de visibilidade é exatamente o de antes — o teste congela o comportamento.

## [1.8.0] — 2026-11-06

### Adicionado

- **Seus produtos na busca do catálogo**: a exploração do catálogo listava só o acervo de fábrica (1.714 itens) — um produto que você mesmo cadastrou era invisível na busca. Agora a mesma busca tem uma seção própria "Seus produtos" acima do acervo, com o mesmo critério amigável das outras buscas (cada palavra precisa aparecer no nome, sem acento e sem maiúsculas — "queijo faz" acha "Queijo da fazenda"). A seção só existe com consulta digitada e sem filtro de setor (o setor filtra o acervo, não o acervo pessoal), teto de 12 resultados, ordenação por nome e a dica de sinônimos herdada do acervo ("Mexerica" mostra "também: Tangerina"). Tocar adiciona o produto à lista aberta como qualquer linha do catálogo. A lógica é um objeto puro (`FiltroDeProdutosLocais`), coberto por testes próprios.

### Refinado
- **A contagem do catálogo diz quando a busca bateu o teto**: uma busca genérica ("leite") corta em 120 resultados — a legenda agora mostra "120 produtos — teto da busca; refine o termo para afinar o resultado" em vez de exibir o número como se fosse tudo. O aviso só aparece sem filtro de setor, quando o rol exibido é inteiramente a busca.
- **"Seus produtos" também entende sinônimos**: na busca do catálogo, digitar "tangerina" agora encontra "Mexerica da feira" gravado por você mesmo — e "mexerica" acha o produto registrado pelo nome oficial, porque a resolução do acervo é bidirecional. Mesma receita estabelecida na busca do Histórico: expansão pela consulta inteira, nunca por palavra solta; sem acervo injetado, o comportamento de antes vale intacto. A dica do campo cita o alcance ("mexerica" acha "Tangerina").
- **A busca do Histórico entende sinônimos do catálogo**: digitar "mexerica" encontra a lista "Tangerina para o suco" — e "tangerina" encontra "Mexerica", porque a resolução do acervo é bidirecional (nome oficial e apelido caem no mesmo item). A expansão é pela consulta inteira, não por palavra solta ("suco de mexerica" não vira duas buscas), e sem resultado a regra de antes vale intacta: cada palavra precisa aparecer no nome da lista ou nas lojas, sem acento e sem maiúsculas. A dica do campo menciona o novo alcance; loja e busca continuam se combinando com E.
- **Busca no Histórico**: campo "Buscar compras" na seção "Compras realizadas" — cada palavra digitada precisa aparecer no nome da lista ou nas lojas da compra, sem acento e sem se importar com maiúsculas (o mesmo critério amigável da Lixeira e do catálogo). Achar "aquela compra no Mercado X" deixa de depender de rolar meses de histórico; busca sem resultado ganha um cartão próprio com o botão de limpar. As analíticas, os números do topo e a variação mensal continuam calculados sobre **todas** as compras — a busca afina a lista, nunca reescreve a estatística. O casamento em si (`TextoUtil.correspondeBusca`) agora é um utilitário compartilhado, usado também pela busca da Lixeira.
- **Filtro por loja no Histórico**: linha de pastilhas logo abaixo da busca — "Todas (N)" e uma pastilha por loja, com a contagem de compras, na mesma receita das categorias da Lixeira e dos setores do catálogo (tocar na pastilha ativa recolhe o filtro). Os rótulos são exatamente os textos dos cartões: "Mercado do Bairro" filtra as compras daquele mercado; compras em mais de um lugar ficam agrupadas no rótulo combinado ("Mercado do Bairro + 2 loja(s)"). Loja e busca se combinam com E, o estado "nada encontrado" explica qual dos dois filtros apertou e o botão "Limpar filtros" abre os dois de uma vez. A lógica é um objeto puro (`FiltroDeHistorico`), coberto por testes próprios.
### Interno
- **Gate de desempenho do parser deixou de medir ruído**: a média de uma única corrida (300 repetições) misturava o custo real do parser com picos do escalonador/GC e reprovava sem nenhuma regressão — no host compartilhado que falhava, mediu-se 1,06–1,17 ms; com o mínimo de 5 lotes de 60 repetições (estimador padrão de microbenchmark para o custo estável), o mesmo host mediu **0,5–0,7 ms**. O orçamento local permanece 1,0 ms da calibração original e os fatores de CI/JaCoCo não mudam; regressões grosseiras de 5× continuam presas em qualquer ambiente.

## [1.7.0] — 2026-11-06

### Adicionado
- **Busca na Lixeira**: campo "Buscar na lixeira" acima dos chips de categoria — cada palavra digitada precisa aparecer no nome ou no detalhe do registro, sem acento e sem se importar com maiúsculas (o mesmo critério amigável do catálogo: "melao" acha "Melão"). Achar "aquela lista que apaguei" deixa de depender de rolar; a busca se combina com o filtro de categoria, o estado vazio explica que nada casou e oferece limpar a busca. Enquanto a busca filtra a visão, os botões de "Esvaziar" ficam escondidos — eles atingem a categoria inteira, e não só o recorte mostrado na tela.
- **Contagem regressiva na Lixeira**: cartões a uma semana ou menos da exclusão permanente ganham um selo com relógio — "some hoje", "some amanhã" ou "some em N dias" (vermelho até 3 dias, âmbar de 4 a 7). Depois disso o cartão segue mostrando apenas a data completa: selo em tudo seria ruído, não informação.

### Segurança
- **Backups à prova de truncamento e corrupção**: o exportador incluiu verificação de integridade nos novos arquivos (PR #23), os identificadores de registros inválidos, duplicados ou sem identidade são rejeitados na importação (PR #28) e o formato v2 passou a ter checksum cobrindo também o cabeçalho (PR #29).
- **Restauração atômica**: exclusões e inserções passaram a rodar dentro de uma transação Room — falha no meio da restauração reverte o banco ao estado anterior, sem dados parcialmente apagados (PRs #19 e #29).
- **Restauração legada (v1) com rede de proteção**: backups antigos, cujo truncamento na cauda é indistinguível de um arquivo válido, só são restaurados após gravar uma cópia de segurança do estado atual, que fica na pasta de backups como "desfazer" (PR #29); layouts divergentes são rejeitados antes de qualquer escrita.
- **Nada escapa do backup**: histórico de preços (PR #22), restrições e alergias (PR #25), configurações, lixeira e aprendizados de substituição (PR #26), JSON aninhado preservado byte a byte (PR #27) e colunas da matriz CSV alinhadas com as lojas (PR #21).

### Interno
- Arquivos de ambiente e segredos locais fora do controle de versão (PR #17).
- Scanner usa `LocalLifecycleOwner` do `lifecycle-runtime-compose` (a variante de `compose.ui.platform` está deprecada); zero avisos de deprecação no código de produção.

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
