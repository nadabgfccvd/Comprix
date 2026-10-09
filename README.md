# Comprix

**Lista de compras + comparador de preços entre mercados. 100% offline.**
Android nativo (Kotlin + Jetpack Compose), pt-BR, sem login, sem nuvem, sem internet.

| | |
|---|---|
| Pacote | `br.com.comprix` — versão 1.0.0 (versionCode 1) |
| SDK | `minSdk 26` (Android 8.0) · `targetSdk 35` · `compileSdk 35` |
| APK debug | `app/build/outputs/apk/debug/app-debug.apk` (cópia pronta em `../apk/Comprix-v1.0-debug.apk`) |
| APK release | `app/build/outputs/apk/release/app-release.apk` (R8 + shrink, assinado) |
| Permissões do APK | **somente** `CAMERA` e `VIBRATE` (sem `INTERNET`, sem `ACCESS_NETWORK_STATE`) |
| Testes | 91 testes JVM · domínio **91,3% de instruções / 95,2% de linhas** |
| Código | 60 arquivos Kotlin (~13,2 mil linhas) + 6 arquivos de teste (~1,2 mil linhas) |

---

## 1. Como compilar e rodar

Pré-requisitos: **JDK 17** e o **Android SDK** (plataforma 35 + build-tools 35.0.0). O wrapper do Gradle já está versionado — não é preciso instalar Gradle.

```bash
# 1. aponte o SDK (ou use a variável ANDROID_HOME)
echo "sdk.dir=/caminho/para/Android/sdk" > local.properties

# 2. APK de debug — este é o entregável principal
./gradlew assembleDebug
#    -> app/build/outputs/apk/debug/app-debug.apk

# 3. APK de release (R8, shrink de recursos, assinado)
./gradlew assembleRelease
#    -> app/build/outputs/apk/release/app-release.apk

# 4. testes da camada de domínio
./gradlew testDebugUnitTest

# 5. relatório de cobertura do domínio
./gradlew jacocoDominioReport
#    -> app/build/reports/jacoco/jacocoDominioReport/html/index.html
```

Instalação no aparelho: `adb install -r app/build/outputs/apk/debug/app-debug.apk` — ou simplesmente copie o `.apk` para o celular e toque nele. **Nada além disso**: o app já abre funcionando, com categorias, OCR e leitor de código de barras prontos no primeiro uso.

> A keystore de release é gerada automaticamente na primeira execução de `assembleRelease` (`keystore/comprix-release.jks`, senha `comprix`), para que o build não exija nenhum passo manual. Para publicar de verdade, troque por uma keystore própria.

---

## 2. Arquitetura

MVVM + Clean Architecture em três camadas, num único módulo Gradle (`:app`) com fronteiras explícitas por pacote — escolha deliberada para manter o build leve e rápido em máquinas modestas, sem abrir mão da separação de responsabilidades.

```
br.com.comprix
├── domain/          ← regras de negócio puras (Kotlin/JVM, zero Android)
│   ├── modelo/          Modelos, Resultados, LeituraDeRotulo (entidades + enums)
│   ├── unidade/         ConversorDeUnidades  — normalização para g / mL / un / m
│   ├── preco/           MotorDePrecos        — R$/unidade-base, kit vs avulso, matriz N lojas
│   ├── categoria/       DicionarioDeCategorias + CategorizadorAutomatico (híbrido)
│   ├── rotulo/          ExtratorDeRotulo + MescladorDeLeituras (pipeline de OCR)
│   ├── nutricional/     ComparadorNutricional (modo técnico, comparação neutra)
│   ├── lista/           InterpretadorDeAdicaoRapida ("2 leite 1l" → campos)
│   └── compra/          CalculadoraDeCompra  — finalização, economia, gastos/categoria
├── data/            ← persistência
│   ├── local/           Room: Entidades, DAOs, Mapeadores, DadosIniciais, ComprixDatabase
│   ├── repositorio/     Catalogo, Lista, Historico, Configuracoes (Flow + suspend)
│   └── backup/          GerenciadorDeBackup — exportar/importar `.cbk`
├── di/              ← ServiceLocator (injeção manual, sem Hilt/Koin)
├── presentation/    ← Compose + Material 3
│   ├── componentes/     tema/ (Cores, Tipografia, Tema), Componentes, Graficos, Feedback, Animacoes
│   ├── navegacao/       Rotas + ComprixNavHost (NavHost único com bottom bar)
│   ├── onboarding/ listas/ detalhe/ comparacao/ scanner/ nutricional/ historico/ configuracoes/
│   └── MainActivity.kt
└── util/            ← TextoUtil, Formatadores, JsonSimples, Constantes
```

**Fluxo de dados:** Room → `Flow` no repositório → `combine`/`stateIn` no ViewModel → `State` imutável na tela. Toda escrita passa por `viewModelScope.launch` + repositório; a UI nunca toca no banco.

**Dependências principais:** Compose BOM 2024.10.01 · Material 3 · navigation-compose 2.8.3 · lifecycle 2.8.7 · Room 2.6.1 (KSP) · CameraX 1.3.4 · ML Kit `text-recognition:16.0.1` e `barcode-scanning:17.3.0` (**variantes bundled**) · coroutines 1.8.1. Build: AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, Gradle Kotlin DSL.

### Por que 100% offline é verificável

O `AndroidManifest.xml` do app não declara `INTERNET` — e ainda **remove**, via `tools:node="remove"`, as permissões `INTERNET`/`ACCESS_NETWORK_STATE`/`WAKE_LOCK` que as bibliotecas do ML Kit injetam na fusão de manifestos. O APK final (confirmado com `aapt2 dump badging`) tem apenas `CAMERA` e `VIBRATE`. Qualquer tentativa de rede em tempo de execução seria barrada pelo próprio Android. Os modelos de OCR e de código de barras vão dentro do APK (`libmlkit_google_ocr_pipeline.so`, `libbarhopper_v3.so`), então **funcionam na primeira abertura, em modo avião, sem download**.

---

## 3. Guia rápido para quem vai usar

**1. Onboarding (uma vez).** Quatro telas explicando o app — criar listas, escanear produtos, comparar mercados, acompanhar o histórico — com o aviso de que tudo fica no aparelho. Dá para pular a qualquer momento.

**2. Minhas listas.** Botão **+** cria uma lista (nome sugerido automaticamente, ex.: "Compras de outubro"). Cada cartão mostra itens, itens comprados e o total estimado. Deslize para excluir; menu para renomear/duplicar.

**3. Dentro da lista — adição rápida.** O campo de baixo entende texto corrido e o teclado nunca fecha entre um item e outro:

| Você digita | O app entende |
|---|---|
| `arroz 5kg` | Arroz · 1 embalagem de 5 kg |
| `2 leite 1l` | Leite · 2 embalagens de 1 L |
| `6x350ml cerveja` | Cerveja · fardo com 6 × 350 mL |
| `3 sabonete` | Sabonete · 3 unidades |

Cada item cai sozinho na categoria certa (Hortifrúti, Mercearia, Limpeza...). Se você corrigir, o app **aprende** aquele nome para sempre. Toque no item para editar nome, quantidade, peso/volume, categoria e observação; toque no preço para registrar o valor na loja ativa.

**4. Comparar preços (ícone de balança).** Monte a matriz **itens × lojas**: o menor preço de cada linha fica destacado em verde, cada loja mostra seu total, os itens que faltam aparecem numa seção própria e a **"compra mista ótima"** diz quanto você economiza comprando cada coisa onde está mais barato — sempre em **R$ e %**. Fardo contra avulso recebe veredito explícito ("o fardo sai 18% mais barato por litro").

**5. Escanear rótulo (ícone de câmera).** Três modos:
- **Foto** — uma foto da gôndola/embalagem.
- **Vídeo guiado (30 s)** — o app conduz por 5 etapas (frente → preço → tabela nutricional → código de barras → validade), extrai ~1 quadro a cada 0,6 s e cruza os resultados por **voto majoritário** campo a campo.
- **Código de barras** — leitura ao vivo.

Depois vem sempre a **tela de revisão**, com o grau de confiança de cada campo: nome, preço, peso/volume, código de barras, validade, fabricação, ingredientes, glúten/alergênicos e selos "ALTO EM...". Você confirma ou corrige antes de salvar.

**6. Finalizar a compra.** Em "Comparar preços" → **Finalizar compra**: escolha a loja de cada item, confirme, e o app registra a compra no histórico com total pago, economia e gastos por categoria.

**7. Histórico.** Gráficos (gastos por mês e por categoria), ticket médio, total economizado e **duplicar lista** a partir de qualquer compra antiga.

**8. Configurações.** Tema claro/escuro/sistema, alto contraste, cores do sistema (desligado por padrão), sons e vibração, **modo técnico** (comparação nutricional — desligado por padrão, com aviso na 1ª ativação), perfil de restrições (sem glúten + alergênicos), gerenciar categorias e **backup**: exportar/importar arquivo `.cbk` e restaurar backups automáticos.

---

## 4. Regras de negócio implementadas (resumo técnico)

- **Unidades.** Tudo é convertido para base: `g`, `mL`, `un`, `m`. `quantidadeBase = quantidade × fator × (itensPorKit ?: 1)`. `pesoOuVolume` é o conteúdo de **uma** embalagem; `quantidade` é o número de embalagens.
- **Preço por unidade-base.** `preço ÷ quantidadeBase`, escala 6 casas (`Constantes.ESCALA_UNIDADE_BASE`); dinheiro em escala 2 com `HALF_EVEN`. Nunca compara dimensões diferentes (kg × L retorna `null`).
- **Kit vs avulso.** Compara o preço por unidade-base das duas opções; diferença < 0,5% é declarada "praticamente igual". A economia é medida sobre a **maior** das duas quantidades e sempre reportada em R$ **e** %.
- **Matriz N lojas.** Células = preço unitário; totais = preço × quantidade. "Menor preço" só é destacado quando há ≥ 2 lojas com preço. Itens marcados como indisponíveis viram "ausentes"; sem preço registrado viram "sem preço". A referência da compra mista é a cesta completa mais barata (ou, se nenhuma loja tem tudo, a de maior cobertura, comparada só no subconjunto que ela cobre).
- **Economia da compra** = `Σ(maior preço observado × qtd) − total pago`, nunca negativa.
- **Categorização híbrida.** memória do usuário → dicionário de ~420 palavras-chave (casamento pela palavra mais longa) → "Outros". 14 categorias semeadas na instalação.
- **OCR (ExtratorDeRotulo).** Pontuação de preço: `+3` se há "R$", `+2 × altura relativa` da linha, `−5` para linhas de lote/CNPJ/validade/tabela nutricional, `−1,5` acima de R$ 500, descarta acima de R$ 9.999. Nome = maior linha sem ruído/preço/data, 3–60 caracteres, dígitos ≤ ⅓ do tamanho. Tabela nutricional lê o **primeiro número depois** do sinônimo do nutriente (evita capturar o %VD). O texto passa por uma normalização *leve* (minúsculas, sem acento, **com** pontuação) — a normalização completa apagaria vírgulas e barras e destruiria preços e datas.
- **Mesclagem de vídeo.** Voto majoritário campo a campo; selos/alergênicos entram se aparecerem em ≥ 30% dos quadros; ingredientes = o texto mais longo lido.
- **Nutricional neutro.** Só marca menor/maior valor por nutriente, com o aviso de que isso "não diz qual é melhor". Selos "ALTO EM..." são **informativos** e jamais entram em ranking (§4.6). Máximo de 3 produtos lado a lado.
- **Backup `.cbk`.** Texto linha a linha (`#COMPRIX-BACKUP v1`, seções `#TABELA`, um JSON por linha). Na importação: valida → `clearAllTables()` → reinsere. Backup automático a cada 24 h, mantendo os 5 mais recentes.

---

## 5. Testes

91 testes JVM em `app/src/test/java/br/com/comprix/`:

| Arquivo | Cobre |
|---|---|
| `ConversorDeUnidadesTest` | conversões, kits, interpretação de texto, dimensões incompatíveis |
| `MotorDePrecosTest` | R$/unidade-base, veredito kit×avulso, matriz de 3 lojas, totais, compra mista, alertas de restrição |
| `RotuloTest` | extração de preço/nome/peso/datas/selos/glúten/alergênicos/tabela, votação entre quadros |
| `ListaECategoriaTest` | adição rápida, dicionário de categorias, aprendizado do usuário |
| `CompraENutricionalTest` | finalização, economia, gastos por categoria, comparação nutricional |
| `UtilTest` | normalização de texto, EAN-13, formatação pt-BR, JSON |

Cobertura medida com JaCoCo (`./gradlew jacocoDominioReport`), excluindo `domain/modelo` (classes de dados):

| Pacote | Instruções |
|---|---|
| `domain.categoria` | 98,7% |
| `domain.preco` | 95,6% |
| `domain.unidade` | 94,8% |
| `domain.lista` | 93,8% |
| `domain.nutricional` | 92,9% |
| `domain.compra` | 92,5% |
| `domain.rotulo` | 84,8% |
| **Domínio (total)** | **91,3% instruções · 95,2% linhas** |

Dois bugs reais foram encontrados pelos testes e corrigidos: (1) a normalização de texto apagava `R$` e `,`, impedindo o OCR de reconhecer preços; (2) `"6x350ml cerveja"` virava o produto `"6x Cerveja"`.

---

## 6. Decisões tomadas e premissas (ambiguidades resolvidas sem perguntar)

1. **Injeção de dependências: Service Locator manual** (`di/ServiceLocator`), não Hilt. Menos processamento de anotações, APK e tempo de build menores — relevante para o hardware-alvo.
2. **Gráficos em Compose puro** (Canvas), sem Vico. O briefing permite "ou equivalente"; evita mais uma dependência (~1 MB) para dois gráficos simples (barras e pizza/rosca).
3. **Configurações no Room** (tabelas de linha única, `id = 1`) em vez de DataStore — um mecanismo de persistência só, e o backup `.cbk` já leva as preferências junto.
4. **Room sem TypeConverters.** Dinheiro em `Long` (centavos), quantidades em `String` (BigDecimal exato), datas em epoch, enums por `name`, listas separadas por `|`, tabela nutricional em JSON (`JsonSimples`). Previsível, rápido e trivial de serializar no backup.
5. **Reordenar itens tem tela própria** (lista plana com setas e arrasto). Arrastar dentro da lista agrupada com cabeçalhos fixos seria ambíguo ("mover para outra categoria?") e pesado em telas de 60+ itens.
6. **Finalizar compra vive só em Comparação** (`comparacao/{listaId}?finalizar=true`), para não duplicar o fluxo em dois lugares.
7. **Preço na lista usa o conceito de "loja ativa"** (chip no topo). Se não houver nenhuma loja cadastrada, o app cria "Meu mercado" automaticamente — ninguém fica travado antes de comparar.
8. **Totais da tela de detalhe usam o menor preço registrado** de cada item (interpretação mais otimista e estável que "último preço").
9. **Modo técnico** cobre exclusivamente a comparação nutricional, desligado por padrão, com diálogo explicativo na primeira ativação.
10. **Cores dinâmicas (Material You) desligadas por padrão**, disponíveis em Configurações — consistência de marca primeiro. Paleta: Verde Esmeralda `#1E8E5A`, Âmbar `#F4B400`, alerta `#D93025`.
11. **Strings pt-BR inline no código**, não em `strings.xml`. O app é monolíngue por decisão de escopo (§13) e isso mantém o texto ao lado do componente que o exibe. `strings.xml` guarda apenas o nome do app e textos de acessibilidade.
12. **Keystore de release gerada pelo próprio build**, para que `assembleRelease` funcione sem passo manual.

## 7. Limitações conhecidas

- **Tamanho do APK: 74 MB (debug) / 64 MB (release).** São os modelos *bundled* do ML Kit para as 4 ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`). Foi uma troca consciente: modelo embarcado é o que garante OCR funcionando offline no primeiro uso. Para um Moto E5 o instalado efetivo é só a ABI do aparelho; se quiser APKs por arquitetura (~25 MB cada), basta adicionar ao `app/build.gradle.kts`:
  ```kotlin
  splits { abi { isEnable = true; reset(); include("armeabi-v7a", "arm64-v8a"); isUniversalApk = true } }
  ```
- **Nenhum teste instrumentado (androidTest).** O ambiente de build não tem emulador nem aparelho; a validação automatizada cobre a camada de domínio (lógica pura), que é onde estão as regras do briefing. Telas e banco foram validados por compilação e revisão manual de fluxo.
- **O OCR é heurístico.** `ExtratorDeRotulo` foi calibrado para etiquetas de gôndola e embalagens brasileiras, mas luz ruim, fonte estilizada ou reflexo degradam a leitura — por isso a tela de revisão pós-OCR é obrigatória e mostra a confiança de cada campo.
- **Vídeo guiado processa os quadros depois da gravação** (`MediaMetadataRetriever`, ~1 quadro/600 ms, no máximo 36 quadros a 1280 px). Analisar em tempo real travaria um Snapdragon 425.
- **Comparação nutricional limitada a 3 produtos** por vez (legibilidade em tela pequena) e só entre produtos que já tenham tabela lida.
- **Backup/restauração não usa `FileProvider`** (compartilhar com outro app): exportar grava em `Documentos/Comprix` do armazenamento interno do app e importar usa o seletor de arquivos do sistema. Evita um provider e permissões extras.
- **Sem migrações de banco** além da versão 1 (`fallbackToDestructiveMigration` não é usado; a versão 1 é a inicial). Mudanças de esquema futuras precisarão de `Migration`.

## 8. Fora de escopo (§13 do briefing)

Não implementados, por decisão explícita: login/conta/sincronização em nuvem, iOS, outros idiomas, pagamentos/cupons/NFC-e, base de preços compartilhada entre usuários, qualquer LLM em tempo de execução, gamificação pesada e uso dos selos "ALTO EM..." para ranquear produtos automaticamente.

---

*Comprix — tudo fica no seu aparelho.*

---

## 9. v1.2.0 — o que mudou (todas as correções reportadas)

Versão de correção de 8 pontos reportados por quem usa, mais a reintegração integral das melhorias v1.1.0 (catálogo-semente com 1.714 produtos, autocomplete, tela de exploração do catálogo, compartilhar lista e o fechamento de 8 funcionalidades órfãs de UI). Nada foi removido; tudo é aditivo ou corretivo.

| # | Relato | O que ficou |
|---|---|---|
| 1 | "Não tem botão de remover minhas listas" | Todo cartão de lista (aberta ou finalizada) tem botão de excluir com diálogo de confirmação; a exclusão apaga itens e preços em cascata e confirma por torrada |
| 2 | "Não mostra as lojas para excluir, renomear ou ver" | Nova tela **Minhas lojas** (ícone na barra da tela Listas): renomear com detecção de nome duplicado, excluir com confirmação (apaga os preços da loja), ver contagem de preços e amostra de produtos, adicionar loja nova |
| 3 | "Segurar o dedo no produto deveria dar opções" | Toque longo no nome do produto abre folha de ações: **Tirar da lista inteira** (com desfazer) e **Não tem nessa loja** (por loja, com "Permitir novamente"), além de "Editar item" |
| 4 | "Aviso de preço faltando deveria deixar preencher na lista" | Tocar no aviso do painel de pendências abre, na própria lista, o mesmo produto com o campo de preço vazio **daquela loja** — salvar preço ou marcar "não tinha nessa loja", sem ir para a aba Comparar |
| 5 | "Minhas alergias/restrições customizadas" | Em Ajustes: cadastre restrições próprias (nome + palavras que denunciam). O app cruza nome e ingredientes dos produtos e avisa na ficha do produto ("Suas restrições") e nos cartões da lista, junto com os alérgenos oficiais (RDC 26/2015) |
| 6 | "Alto contraste não é forte o suficiente" | Paletas reforçadas ao nível AAA (≥ 7:1 texto, ≥ 4,5:1 componentes) nos dois temas, bordas de cartão quase pretas/brancas, e o alto contraste agora também vence o Material You no esquema de cores interno |
| 7 | "Vibração e sons não funcionam / permissões" | Motor de feedback novo: vibração real (VibrationEffect, compatível com API 26 e 31+) e tons curtos (ToneGenerator) ao marcar item e gravar preço; VIBRATE é pedida ao abrir; as chaves de Ajustes passam a mandar de verdade |
| 8 | "Aba Listas trava ao comparar estabelecimentos" | Corrigido o caminho de navegação: voltar à raiz "listas" agora desmonta a pilha de comparação mesmo com a lista aberta, e a aba Listas fica marcada também dentro da lista aberta |

**Qualidade:** 178 testes JVM passando (9 novos do catálogo, 11 novos das restrições personalizadas), builds debug e release verdes, versão 1.2.0 (versionCode 3), mesma assinatura de release.

## 10. v1.3.0 — o que mudou (relato de uso da v1.2.0)

| # | Relato | Correção |
|---|--------|----------|
| 1 | "Digite um preço maior que zero" com preço já digitado (folha Preço faltando) | O botão salvava lendo o estado confirmado por evento de foco, que chega DEPOIS do toque. Agora `CampoDePreco` espelha o texto vivo (`aoDigitar`) e o botão parseia o texto direto com `TextoUtil.paraDecimal` (aceita vírgula). Aplicada a mesma receita na folha de preço da matriz (Comparação). |
| 2 | Preços e pesos ignorados em listas coladas ("Uva 1kg 3,99,limao 1kg 6,50,...") | Preço agora é extraído POR SEGMENTO (`ItemInterpretado.preco`); cada item com preço na linha grava na loja ativa. Inteiro solto no fim ("cafe 3") continua sendo quantidade; preço exige vírgula/ponto decimal ou prefixo R$. |
| 3 | "maça de peito" encolhia para "Maca" | Nome digitado é preservado quando tem palavras além do casamento com o catálogo; palavras de embalagem só são descartadas como PREFIXO ("pacote de asa" → "Asa"; "de peito" fica). |
| 4 | Sem desfazer/refazer da adição | Botões desfazer/refazer na caixa de adição rápida. Desfazer remove os itens criados e devolve TUDO que estava escrito para a caixa; refazer reaplica e limpa. |
| 5 | Sem arrastar-e-soltar | Puxador no cartão do item: segurar e arrastar reordena dentro da seção; a nova ordem é persistida (`ordemManual`) e sobrevive a fechar o app. |
| 6 | Lojas invisíveis no Comparar sem lista | Novo estado do Comparar sem lista: todos os estabelecimentos com contagens, renomear/excluir por cartão e botão "Gerenciar lojas". |
| 7 | Lojas sem editar/excluir | Ações de renomear/excluir alcançáveis do Comparar (folha de opções por loja) e da tela Minhas lojas; exclusão da loja ativa transiciona com segurança. |
| 8 | "Tangerina — também buscar: mexerica, bergamota" aparecia colado | A semente separa o nome dos sinônimos (`sinonimos`); buscar "mexerica"/"bergamota" resolve o produto Tangerina. 36 itens da semente convertidos; reparo de dados renomeia produtos antigos com nome poluído. |
| 9 | Alinhamento/centralização | Varredura: títulos de folhas/diálogos centrados, linhas com `CenterVertically`, números à direita, botões de rodapé com larguras iguais, puxador alinhado. |

Versão: versionCode 4 / versionName 1.3.0. 193 testes JVM passando.

### 11. v1.4.0 — correções de listas, parser sem vírgula, favoritos, lixeira e 10 funcionalidades novas

**Correções:** desfazer/refazer sobrevive à navegação entre abas (histórico por lista, singleton de processo); duplicação numerada "Compra do Mês (cópia 28)" sem empilhar "(cópia)"; seleção em massa por toque longo (excluir/favoritar/finalizar com confirmações); bloqueio de re-finalização ("Esta conta já foi finalizada"); parser separa itens colados por espaço ("Bolo de laranja 1kg 11,99 arroz 5kg 20,33" → 2 itens); sinônimos reapresentados ("também buscar: mexerica, bergamota") na lista e na ficha; contagens de Minhas lojas = detalhes do "Ver" (dedupe por nome normalizado, lista completa com scroll); drag-and-drop ENTRE categorias (atualiza a categoria do produto e as duas ordens); kit mostra "≈ R$ X por unidade" e total do kit na edição; ícones da barra inferior e switches realinhados; gráfico de Analíticas com grade/eixo e tendência = média móvel real; tutoriais por modo no scanner (foto/vídeo/código) com erros acionáveis; "O que o Comprix aprendeu" centralizado.

**Novidades (pesquisadas com fontes — ver worklog Task 6):** favoritos de listas e itens com adição de 1 toque; Lixeira por categoria com restauração, dupla confirmação e purga automática de 30 dias; orçamento por lista com barra e alerta de estouro; Modo compras (progresso grande, ocultar comprados, texto ampliado); "Comprar de novo" (top produtos mais anotados); painel de validades próximas; alerta "você já viu mais barato" (mínimo do histórico por produto); meta de economia mensal nas Analíticas; exportar compras em CSV; entrada por voz na lista; widget da lista na tela inicial; dividir a conta por pessoas no resumo da compra.
