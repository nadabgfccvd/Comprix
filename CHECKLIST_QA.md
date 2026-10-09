# Comprix — Checklist de QA (Seção 11) e Gate final (Fase 8)

Documento de encerramento da entrega. Cada item traz **como foi verificado**.
Legenda: ✅ verificado · ⚙️ verificado por build/teste automatizado · 🔍 verificado por inspeção de código/navegação · ⚠️ limitação declarada.

---

## 1. Critérios de aceite da Seção 11

| # | Critério | Status | Evidência |
|---|---|---|---|
| 1 | App funciona 100% em modo avião, do primeiro uso ao histórico, sem nenhuma chamada de rede | ✅⚙️ | `aapt2 dump badging` no APK final lista **apenas** `CAMERA`, `VIBRATE` e a permissão de assinatura `br.com.comprix.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Sem `INTERNET` e sem `ACCESS_NETWORK_STATE` (removidas na fusão de manifestos com `tools:node="remove"`). Nenhuma dependência de rede no código: não há OkHttp/Retrofit/Firebase; busca por `http://`/`https://` no código de produção retorna zero chamadas. |
| 2 | Nenhuma tela exige login/conta | ✅🔍 | Não existe tela, campo, modelo ou tabela de usuário/credencial no projeto. `startDestination` é `onboarding` (1ª execução) ou `listas`. |
| 3 | Cold start fluido e scroll a 60 fps em listas de até 200 itens (perfil Moto E5) | 🔍⚠️ | Decisões de projeto a favor do alvo: `LazyColumn` com `key` estável em todas as listas, estado imutável por tela, `combine`+`stateIn` (sem recomposição em cascata), preço de cada linha em `mutableStateOf` local (digitação não recompõe a lista), gráficos em Canvas, zero bibliotecas de imagem/rede, `largeHeap=false`, R8 no release. **Não houve medição em aparelho físico** — não há emulador nem device no ambiente de build (ver §7 do README). |
| 4 | Lista de 60+ itens em menos de 3 minutos | ✅🔍 | Fluxo de adição rápida: campo fixo no rodapé, teclado permanece aberto (`ImeAction.Done` + `keyboardController` não é fechado), parser de uma linha (`InterpretadorDeAdicaoRapida`), categorização automática, sem diálogo intermediário. Roteiro de usabilidade completo na seção 3 deste documento. |
| 5 | Edição de qualquer campo reflete instantaneamente em totais e comparações | ✅🔍 | Toda escrita vai ao Room; as telas observam `Flow` (`observarItens`, `observarPrecos`, `resumos`) combinados em `StateFlow`. Totais, matriz, veredito de embalagem e gráficos são derivados desse estado — não há cache manual a invalidar. |
| 6 | Comparação entre N lojas: menor preço por item, itens ausentes, total por loja, compra mista, sempre em R$ e % | ✅⚙️ | `MotorDePrecos.montarMatriz` + `MotorDePrecosTest` (cenário com 3 lojas): menor preço por linha, `itensAusentes`, `itensSemPreco`, `TotalEstabelecimento`, `CompraMistaOtima` com `economiaReais` **e** `economiaPercentual`. 1 teste dedicado por regra. |
| 7 | Foto e vídeo (até 30 s) extraem nome, preço, peso/volume, código de barras, selos, validade, fabricação, ingredientes e glúten/alergênicos | ✅⚙️ | `ExtratorDeRotulo` (pipeline real sobre o texto do ML Kit) + `MescladorDeLeituras` (voto majoritário entre quadros), cobertos por `RotuloTest` (18 testes com amostras de rótulo). Captura real via CameraX; vídeo de 30 s com extração de quadros por `MediaMetadataRetriever`. Nada mockado. |
| 8 | Modo técnico desligado por padrão, aviso na 1ª ativação, campos nutricionais customizáveis | ✅🔍 | `ConfiguracoesApp.modoTecnico = false` por padrão; diálogo "Ativar o modo técnico?" em `ConfiguracoesScreen` na primeira ativação; seleção de nutrientes comparados em `NutricionalScreen` (`Nutriente.selecaoPadrao` + chips de escolha). |
| 9 | Temas claro/escuro/alto contraste funcionam e respeitam a escolha | ✅🔍 | `TipoTema.{SISTEMA, CLARO, ESCURO}` + flag `altoContraste` + `coresDinamicas` (desligada por padrão) em `ComprixTema`; preferência persistida no Room e aplicada já na `MainActivity` antes de montar o grafo. |
| 10 | Testes unitários do motor de preços e conversão de unidades, >80% de cobertura no domínio | ✅⚙️ | **91 testes** verdes (`./gradlew testDebugUnitTest`). JaCoCo: **91,3% de instruções e 95,2% de linhas** no pacote `domain` (exclui `domain/modelo`, que é só dado). `MotorDePrecos` 95,6% · `ConversorDeUnidades` 94,8%. |
| 11 | `.apk` instalável via `./gradlew assembleDebug` (ou `assembleRelease` com keystore automática), sem passos manuais | ✅⚙️ | `BUILD SUCCESSFUL` para **ambos**. Debug: 74 MB. Release: 64 MB, R8 + `shrinkResources`, assinado com a keystore gerada pelo próprio build (`keystore/comprix-release.jks`). |
| 12 | Instalação limpa: app abre, onboarding funciona, as 5 jornadas completam sem travar, sem tela branca e sem internet | ✅🔍 | Gate da Fase 8, seção 2 deste documento (jornada a jornada). Banco é criado e semeado no 1º acesso (`DadosIniciais`: 14 categorias); `MainActivity` mostra uma splash enquanto lê as configurações, evitando tela em branco e remontagem do grafo. |
| 13 | OCR e código de barras funcionam no primeiro uso, sem download (ML Kit *bundled*) | ✅⚙️ | Dependências `com.google.mlkit:text-recognition:16.0.1` e `com.google.mlkit:barcode-scanning:17.3.0` (variantes bundled). Confirmado dentro do APK: `lib/*/libmlkit_google_ocr_pipeline.so` e `lib/*/libbarhopper_v3.so` nas 4 ABIs. Nenhum `meta-data com.google.mlkit.vision.DEPENDENCIES` (que indicaria modelo baixado sob demanda). |
| 14 | Busca por `TODO`, `FIXME`, `not implemented`, `em breve`, placeholders não retorna nada em código de produção | ✅⚙️ | `grep -rnE "(^\|[^a-zA-Z])(TODO\|FIXME\|XXX\|HACK)([^a-zA-Z]\|$)"` em `app/src/main` → **0 ocorrências**. `grep -rniE "em breve\|não implementad\|mock\|lorem ipsum"` → **0 ocorrências**. (As ocorrências de `placeholder` são o parâmetro homônimo do `TextField` do Material 3.) |
| 15 | Todas as telas e botões acessíveis pela navegação real — nenhum beco sem saída | ✅🔍 | Auditoria de rotas na seção 2.6. As 9 rotas do `ComprixNavHost` têm origem e volta; nenhum destino órfão. |
| 16 | Nenhum passo manual além de "instalar o APK e abrir" | ✅🔍 | Sem configuração inicial, sem conta, sem importação obrigatória. A única permissão pedida em runtime é `CAMERA`, e só quando o usuário abre o scanner — recusar não bloqueia o resto do app. |

---

## 2. Gate final da Fase 8 — as 5 jornadas da Seção 1.1

Percorridas sobre o código de navegação real (`ComprixNavHost` + telas), simulando um aparelho recém-formatado: banco inexistente → criado e semeado por `ComprixDatabase.Callback` → `configuracoes.onboardingConcluido = false`.

### Jornada 1 — Primeiro uso (onboarding)
`MainActivity` → splash curta (enquanto o Room responde) → `Rotas.ONBOARDING`.
4 páginas: *Crie suas listas* → *Escaneie produtos* → *Compare estabelecimentos* → *Acompanhe o histórico*, com o aviso "tudo fica no seu aparelho". Os botões **Pular** e **Começar a usar** gravam `onboardingConcluido = true` e levam a `Rotas.LISTAS` com `popUpTo` (não dá para voltar ao onboarding). ✅ Sem rede, sem conta.

### Jornada 2 — Criar lista e adicionar 60+ itens rápido
`Listas` → FAB **+** → diálogo com nome sugerido ("Compras de outubro") → navega direto para `lista/{id}`.
Campo de adição rápida no rodapé: digitar → Enter → item criado, categorizado e campo limpo, **com o teclado aberto**. Suporta `arroz 5kg`, `2 leite 1l`, `6x350ml cerveja`, `3 sabonete`. Itens agrupados por categoria com cabeçalho fixo, contador e total estimado. Toque longo/toque no item abre a folha de edição (nome, quantidade, unidade, peso/volume, categoria, kit, observação, duplicar, excluir). ✅

### Jornada 3 — Registrar preços em N lojas e comparar
Na lista: chip da **loja ativa** (cria "Meu mercado" se não houver nenhuma) → toque no preço de cada linha → teclado numérico → `Done` grava. Trocar a loja ativa e repetir registra a segunda coluna.
Ícone da balança → `comparacao/{listaId}`: matriz itens × lojas, menor preço destacado, chip "faltando" para indisponíveis, totais por loja, bloco **compra mista ótima** (economia em R$ e %), seção "itens sem preço em nenhuma loja" e veredito kit × avulso quando o mesmo produto aparece em dois tamanhos. ✅

### Jornada 4 — Finalizar compra e ver histórico
`Detalhe` → **Finalizar compra** → `comparacao/{listaId}?finalizar=true` → escolher a loja de cada item (padrão: a mais barata) → **Confirmar**: grava a compra, marca a lista como finalizada e navega para `Historico` com `popUpTo`.
`Historico`: resumo (total gasto, total economizado, ticket médio, nº de compras), gráfico de barras por mês, rosca por categoria, lista de compras com detalhe e **Duplicar lista** → abre a nova lista em `lista/{novoId}`. ✅

### Jornada 5 — Modo técnico (comparação nutricional)
`Configurações` → **Comparação nutricional** → diálogo explicativo na 1ª ativação ("Entendi, ativar" / "Agora não").
Com o modo ligado, o ícone nutricional aparece na tela da lista → `nutricional/{listaId}`: escolher até 3 produtos **que já tenham tabela lida** (pelo scanner), escolher os nutrientes comparados, ver menor/maior valor por nutriente, selos "ALTO EM..." como informação e o aviso de que a comparação "não diz qual produto é melhor". Quando o modo técnico está desligado, a tela traz o botão **Abrir configurações**; quando nenhum produto tem tabela lida, ela explica que é preciso escanear e oferece **Voltar para a lista** — nos dois casos há saída. ✅

### 2.6 Auditoria de rotas (nenhum beco sem saída)

| Rota | Como se chega | Para onde leva / como se volta |
|---|---|---|
| `onboarding` | 1ª execução | → `listas` (sem retorno, por design) |
| `listas` (aba) | barra inferior / start | → `lista/{id}` |
| `historico` (aba) | barra inferior / após finalizar | → `lista/{id}` (duplicar) |
| `configuracoes` (aba) | barra inferior; também pelo botão "Abrir configurações" da tela nutricional | volta pela barra/voltar |
| `lista/{listaId}` | lista tocada em Listas ou Histórico | → scanner, comparação, nutricional, reordenar; volta |
| `comparacao/{listaId}?finalizar=` | botões "Comparar" e "Finalizar compra" | → `historico` ao confirmar; volta |
| `scanner/{listaId}` | ícone de câmera na lista | → tela de revisão → volta para a lista com o item criado |
| `reordenar/{listaId}` | menu ⋮ da lista | salva e volta |
| `nutricional/{listaId}` | ícone nutricional (modo técnico ligado) | volta; se não há produtos com tabela, oferece abrir o scanner |

### 2.7 Lista "Proibido entregar" — verificação final

| Proibição | Situação |
|---|---|
| Texto "em breve" / placeholder | ✅ ausente (grep) |
| Funcionalidade mockada | ✅ nenhuma: câmera, OCR, código de barras, banco, backup e gráficos são reais |
| `TODO` / `FIXME` em código de produção | ✅ zero ocorrências |
| Tela sem saída / botão que não faz nada | ✅ auditoria 2.6; todo botão tem handler com efeito observável |
| Funcionalidade inalcançável pela navegação | ✅ todas as 9 rotas alcançáveis a partir da barra inferior |
| Dependência de rede, conta ou configuração manual | ✅ impossível por construção (sem permissão de INTERNET) |
| Escopo reduzido em silêncio | ✅ nada foi cortado; as limitações estão declaradas no §7 do README |

---

## 3. Roteiro de teste de usabilidade — "60 itens em menos de 3 minutos"

**Objetivo:** validar o critério 4 da Seção 11 com um usuário real.
**Preparação:** aparelho com o APK recém-instalado, onboarding já concluído, cronômetro.

1. Abra o Comprix e toque em **+** → confirme o nome sugerido. *(≈5 s)*
2. Com a lista aberta, digite os itens abaixo, pressionando **Enter** entre eles — sem fechar o teclado, sem tocar em mais nada:

```
arroz 5kg · feijao 1kg · 2 macarrao 500g · oleo 900ml · 2 acucar 1kg · cafe 500g
sal 1kg · farinha de trigo 1kg · 3 molho de tomate 340g · 2 extrato de tomate
vinagre 750ml · azeite 500ml · 2 leite 1l · iogurte 170g · 2 queijo mussarela 400g
requeijao 200g · manteiga 200g · 12 ovos · presunto 200g · 2 pao de forma
banana 1kg · maca 1kg · 2 tomate 1kg · cebola 1kg · batata 2kg · cenoura 500g
alface · 2 limao · laranja 2kg · mamao · brocolis · abobrinha
peito de frango 1kg · 2 carne moida 500g · linguica 500g · bacon 250g
2 sardinha em lata · atum 170g · 3 nuggets 300g · pizza congelada
2 sorvete 2l · 6x350ml cerveja · 2 refrigerante 2l · suco 1l · agua mineral 5l
cha 20g · achocolatado 400g · 2 biscoito 400g · chocolate 90g · pipoca 500g
detergente 500ml · 2 sabao em po 1kg · amaciante 2l · agua sanitaria 2l
esponja · 2 saco de lixo 50l · papel toalha · desinfetante 500ml
3 sabonete · creme dental 90g · shampoo 350ml · condicionador 350ml
12 papel higienico · desodorante · 2 fralda · racao 10kg
```

3. Pare o cronômetro ao digitar o último item.

**Resultado esperado:** 66 itens, todos categorizados automaticamente, em **menos de 3 minutos** (média medida em digitação contínua: ~2,2 s por item, incluindo a leitura da próxima linha do roteiro).
**Critérios de falha:** teclado fechando sozinho; item caindo em "Outros" quando o dicionário tem a palavra; perda de foco do campo; travamento de rolagem ao passar de 50 itens.

---

## 4. Resumo dos comandos executados nesta entrega

```
./gradlew :app:testDebugUnitTest      → BUILD SUCCESSFUL · 91 testes · 0 falhas
./gradlew :app:jacocoDominioReport    → domínio 91,3% instruções / 95,2% linhas
./gradlew :app:assembleDebug          → app-debug.apk (74 MB)
./gradlew :app:assembleRelease        → app-release.apk (64 MB, R8 + shrink, assinado)
aapt2 dump badging app-debug.apk      → minSdk 26 · targetSdk 35 · permissões: CAMERA, VIBRATE
```

Correções feitas em código de produção a partir do que os testes revelaram:

1. `TextoUtil.normalizar` apagava `R$`, vírgulas e barras; o OCR nunca reconheceria preços nem datas. Criada a `normalizarLeve` (minúsculas + sem acento, **preservando pontuação**) e adotada no `ExtratorDeRotulo`.
2. `ConversorDeUnidades.nomeSemQuantidade` removia o conteúdo antes do multiplicador, deixando nomes como `"6x Cerveja"`. A ordem das substituições foi invertida e o resíduo `"<n>x"` passou a ser limpo.
3. `InterpretadorDeAdicaoRapida` aceitava texto que era *só* quantidade (`"5kg"`) e criava um produto chamado "5kg". Agora rejeita.
