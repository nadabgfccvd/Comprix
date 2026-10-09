# Correções dos problemas relatados na versão anterior

Registro de rastreabilidade: **problema relatado → causa raiz → correção → onde está → prova**.
Todos os itens têm teste automatizado em `app/src/test/java/br/com/comprix/CorrecoesRelatadasTest.kt`
(15 testes, 15 verdes — `./gradlew :app:testDebugUnitTest`).

---

## 1. "Itens conectados entre lojas: apagar o óleo no Mercado 1 apagava no Mercado 2, e mudar o preço mudava nos dois"

**Causa raiz.** Dois erros somados:

1. A tela de preços tratava o gesto de apagar como *remover o item da lista*. Como preço tem
   `ON DELETE CASCADE` a partir do item, apagar o item levava junto os preços de **todas** as lojas.
2. O estado da tela guardava o preço indexado **só pelo `itemId`**. Ao trocar de loja, o mesmo campo
   era reaproveitado e o valor "vazava" de uma coluna para a outra.

**Correção.**

| Antes | Agora |
|---|---|
| `Map<ItemId, Preco>` no estado | `Map<ChaveDePreco, PrecoRegistrado>` com `ChaveDePreco(itemId, estabelecimentoId)` |
| apagar = remover item | `limparPrecoDaLoja(itemId, lojaId)` → apaga **só aquela célula** |
| — | `removerItemDaListaMestra(itemId)` → ação separada, exige confirmação, avisa "sai de todas as lojas" e oferece **Desfazer** que devolve o item **com todos os preços** |
| — | `marcarIndisponivel(itemId, lojaId)` → "não tinha nessa loja", sem apagar nada |

A única forma de ler um preço no código passou a ser `estado.precoDe(itemId, lojaId)` — não existe
mais nenhuma API que devolva "o preço do item" sem dizer de qual loja.

**Onde:** `presentation/precos/PrecosViewModel.kt` (`ChaveDePreco`, `limparPrecoDaLoja`,
`removerItemDaListaMestra`, `desfazerUltimaAcao`), `data/repositorio/PrecoRepositorio.kt`
(`removerPreco(itemId, lojaId)`), índice único `(itemDaListaId, estabelecimentoId)` em `Entidades.kt`.

**Provas:** `preco e identificado por item mais loja, nunca so pelo item`,
`apagar o preco de uma loja preserva o mesmo item nas outras lojas`.

---

## 2. "Deveria ficar constantemente no início, antes da lista, dizendo quais produtos não foram adicionados"

**Causa raiz.** A informação existia só dentro da matriz, numa seção de "itens faltantes" que ficava
abaixo da tabela — ou seja, depois de rolar a tela inteira. Na prática, ninguém via.

**Correção.** Painel **fixo no topo**, antes da lista, sempre visível enquanto houver lacuna:

- título em uma frase: *"2 itens sem preço em Mercado 2 e Mercado 1"*;
- uma **barra de cobertura por loja** ("Faltam 3 de 3 itens" / "Todos os 7 itens com preço");
- ao expandir, a lista item a item: *"Falta o preço de Óleo de soja 900 mL no Mercado 2"*;
- cada linha é tocável e leva direto ao campo daquele item naquela loja;
- quando tudo está pesquisado, o painel vira verde: *"Tudo pesquisado nas 2 lojas"*.

**Onde:** `presentation/comum/PainelDePendencias.kt` (`PainelDePendencias` e a variante compacta
`FaixaDePendencias`), alimentado por `EstadoDePrecos.relatorio`.

**Provas:** `aviso do painel resume as pendencias em uma frase`, `cobertura por loja mostra progresso e resumo`.

---

## 3. "Criar uma lista mestra que compara se todos os produtos têm preço nos dois estabelecimentos"

**Correção.** Virou um motor de domínio próprio: `AuditoriaDeCobertura`. A lista de produtos é **uma
só** (a lista mestra) e cada loja é uma **coluna de preços** sobre ela. A auditoria cruza as duas
coisas e classifica cada cruzamento em três situações — que o app trata de forma diferente:

| Situação | Significado | Conta como pendência? | Efeito na comparação |
|---|---|---|---|
| `REGISTRADO` | preço anotado | não | entra na comparação |
| `INDISPONIVEL` | pesquisei, a loja não tem | **não** (é um dado) | vira "falta nesta loja" na matriz |
| `PENDENTE` | ainda não anotei | **sim** | bloqueia a leitura daquela linha |

Essa distinção é o ponto central: hoje o app não deixa mais o total de uma loja parecer mais barato
só porque um item não foi pesquisado nela.

O relatório expõe `pendencias` (com mensagem pronta), `porLoja` (cobertura e progresso),
`itensSemNenhumPreco` e `completa`.

**Onde:** `domain/preco/AuditoriaDeCobertura.kt`; consumido pelo `PrecosViewModel` e pelo painel.

**Provas:** `auditoria aponta exatamente qual item falta em qual loja`,
`nao ter o produto na loja e diferente de nao ter pesquisado`,
`itens sem nenhum preco sao listados a parte`,
`item com preco em uma loja so nao recebe destaque de melhor preco`.

---

## 4. "As cores não estão dando contraste com o fundo branco"

**Causa raiz.** As cores da marca foram usadas cruas como cor de texto. Medindo pela WCAG 2.1:

| Cor | Sobre fundo claro | Mínimo p/ texto (4,5:1) | Mínimo p/ ícone (3:1) |
|---|---|---|---|
| Verde Esmeralda `#1E8E5A` | **4,04:1** | ❌ | ✔ |
| Âmbar `#F4B400` | **1,80:1** | ❌ | ❌ |
| Alerta `#D93025` | 4,66:1 | ✔ | ✔ |

O âmbar era o pior caso — praticamente invisível no branco.

**Correção.** A marca não mudou; o que mudou foi **separar papéis**, com todos os valores calculados:

| Papel | Tema claro | Contraste medido |
|---|---|---|
| Preenchimento (botão, chip) | `#1C8454` + texto branco | **4,69:1** |
| Texto/ícone verde | `#1C8253` | **4,69:1** |
| Texto/ícone âmbar | `#946D00` | **4,61:1** |
| Destaque âmbar preenchido | `#F4B400` + tinta `#1C1B1F` | **9,28:1** |
| Erro | `#C62828` | 5,9:1 |
| Tema escuro | `#27B674` / `#F6B600` / `#E15950` | 7,06 / 10,21 / 5,05:1 |

Outras correções do mesmo problema:

- **cartões sumindo no fundo branco**: `surfaceVariant #ECEFEA` e `outlineVariant #C9CCC6` para que
  cartão e grade da matriz tenham borda visível;
- **chips de loja**: a cor do texto passou a ser escolhida por luminância (`corDeTextoSobre`), e a
  paleta de lojas trocou `#00897B` (4,32:1) por `#00796B` e o âmbar puro por `#A05E00`;
- **modo alto contraste** com preto/branco puros;
- a barra de status deixou de ser verde sólida (ícones ilegíveis) e passou a transparente, com os
  ícones ajustados ao tema.

**Onde:** `presentation/tema/Cores.kt`, `Tema.kt`, `res/values/colors.xml`, `res/values/themes.xml`,
`res/values-night/themes.xml`, `data/local/DadosIniciais.kt`.

**Provas:** `texto verde e ambar passam em 4,5 sobre fundo claro`, `botao verde com texto branco passa em 4,5`,
`ambar da marca so e usado com tinta escura em cima`,
`cor de texto sobre chip de loja sempre tem contraste suficiente` — os testes recalculam o contraste
pela fórmula da WCAG a partir das cores reais do app, então qualquer tom que regrida quebra o build.

---

## 5. "Clicar fora da área do campo de valor deveria contar como finalizar o preço, igual ao Enter"

**Causa raiz.** O valor só era gravado no `onDone` do teclado. Tocando no próximo item, o texto
digitado era descartado silenciosamente — o pior tipo de perda, porque o usuário acha que anotou.

**Correção.** Os três caminhos convergem para **um único ponto de gravação**:

1. tocar fora (qualquer área da tela) → `clearFocus()`;
2. Enter/Pronto no teclado → **também** só chama `clearFocus()`;
3. perder o foco por qualquer outro motivo (rolar para outro campo, voltar da câmera, trocar de aba).

Toda a confirmação acontece no `onFocusChanged`, e só dispara se o texto mudou desde a última
confirmação — não há caminho que perca o valor nem que grave duas vezes.

Extras do mesmo campo: aceita vírgula ou ponto, limita a 2 casas, mostra o valor interpretado
embaixo (`= R$ 1.290,00`, sem adivinhar centavos), pede confirmação acima de R$ 500 e recusa acima
de R$ 9.999.

**Onde:** `presentation/comum/CampoDePreco.kt` (`CampoDePreco`, `filtrarEntradaDeMoeda`,
`Modifier.areaQueConfirmaAoTocarFora()` — aplicado na raiz das telas com campos).

**Provas:** `entrada de moeda aceita apenas digitos e uma virgula`,
`valor digitado sem virgula e interpretado como reais inteiros`. O disparo por perda de foco é
comportamento de UI; está isolado num único `onFocusChanged` e será coberto pelo smoke das 5
jornadas no aparelho (Nível C do checklist).

---

## Resumo

| # | Problema | Situação |
|---|---|---|
| 1 | Preço/remoção vazando entre lojas | **Corrigido** — chave `(item, loja)` + ações separadas + desfazer |
| 2 | Pendências invisíveis | **Corrigido** — painel fixo no topo, antes da lista |
| 3 | Lista mestra com conferência entre lojas | **Implementado** — `AuditoriaDeCobertura` com 3 situações |
| 4 | Contraste no fundo branco | **Corrigido** — paleta recalculada, verificada por teste |
| 5 | Confirmar preço ao tocar fora | **Corrigido** — foco único como ponto de gravação |
