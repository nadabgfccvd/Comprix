# Parser de texto livre — Comprix

Converte uma linha digitada ("2kg de carne, meia dúzia de ovos e 3 leite") em itens de lista. Roda
**offline**, é **determinístico** (tabela + regra, sem modelo estatístico) e vive em
`app/src/main/java/br/com/comprix/domain/parser/` — pacote puro, sem `android.*`.

## Como está organizado
| Arquivo | Papel |
|---|---|
| `ModelosDoParser.kt` | contratos: `ItemInterpretado`, `TokenDeQuantidade`, `SugestaoNaoBloqueante`, `MemoriaDoParser` |
| `DivisorDeLinha.kt` | quebra a linha em itens (P1–P4: vírgula, "e", quebra, numeral no início) |
| `TabelaDeQuantidades.kt` | 87 tokens: coletivos, embalagens e apelidos numéricos (bicho/bingo) |
| `NumeraisPorExtenso.kt` | 0 a 50.000 **por regra** de composição, não por lista |
| `LexicoDeItens.kt` | 302 itens nas 14 categorias, com classe, unidade padrão e colisões |
| `FalsosPositivos.kt` | 27 guardas ("meia calça", "dúzia de motivos") + quantidades indefinidas |
| `ParserDeLinhaDeCompra.kt` | a leitura em si: quantidade → unidade → nome → categoria → confiança |
| `SugestoesDoParser.kt` | gatilhos G1–G8: no máximo 1 pergunta por item, 2 opções, sempre inline |
| `QuantidadeEmPosicaoNumerica.kt` | modo restrito: só converte onde só cabe número |

A ponte com o banco é `data/parser/EntradaDeTextoLivre.kt` (carrega a memória, resolve a categoria e grava
o item). O aprendizado local fica em `decisoes_parser` (Room v2), sai no backup `.cbk` e é apagável.

## Regras que não se negociam
- **Nunca inventa**: apelido sem fonte não entra. 53 números entre 1 e 90 não têm apelido verificado e
  estão **declarados** como lacuna em `comprix-parser-spec.json`.
- **Nunca bloqueia**: linha irreconhecível volta com o texto literal e um motivo legível ("dúzia de
  carne" gera alerta, não recusa).
- **Nunca converte fora de posição numérica**: "patinho" é corte bovino, mas "dois patinhos na lagoa de
  arroz" é 22 de arroz (Regra A). Unidades só do conjunto canônico; categorias só as 14 oficiais.
- Apelidos de conteúdo sensível (ex.: 24) existem na tabela, marcados em `conteudoSensivel`, e **nunca**
  são oferecidos pela interface.

## Verificação
`./gradlew :app:testDebugUnitTest --tests "br.com.comprix.parser.*"` → **39 testes**, incluindo os
**155 casos** de `comprix-parser-testes.json`. O `comprix-parser-spec.json` é **gerado** por
`EspecificacaoDoParserTest` a partir das tabelas vivas — não existe cópia escrita à mão para divergir.

**Desempenho medido** (JVM do build, `app/build/relatorios/desempenho-parser.txt`): 0,93 ms no pior caso
(179 caracteres, 11 itens numa linha só) e 0,06 ms na linha típica de 1 item. Mesmo aplicando um fator de
8× para a CPU do Moto E5, o pior caso fica em ~7 ms — dentro do quadro de 16 ms. Limite travado por teste.
