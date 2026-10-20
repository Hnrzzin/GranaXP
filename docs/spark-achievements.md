# Conquistas e sequência na V1 Spark

Estado registrado no Changelog 129, em 19/10/2026: publicação concluída com
resultado `PUBLISHED_VERIFIED` em `granaxp-d7927/(default)`. Uma única tentativa
autorizada criou as cinco novas conquistas; as três históricas mantiveram seus
campos e versões. A reconciliação independente confirmou oito definições,
sem documentos extras ou órfãos.

Digest da proposta publicada:
`14f9e0d048437253ed600f3823bcc4fade58799fa707d511a98d2bc70c0613e5`.
O [registro administrativo](achievement-publish.md) preserva o procedimento e
seus comandos como histórico. A tentativa autorizada foi consumida: **não
repetir a publicação**, remover o journal ou alterar a proposta para reaplicá-la.

Esses resultados vêm das evidências do Changelog 129, não de uma nova consulta
ao Firebase nesta revisão documental. A operação administrativa enviou somente
os cinco creates e não leu nem escreveu dados privados de usuários/progresso.
A inspeção no AVD confirmou os oito cards e o indicador de sequência; isso não
comprova todas as trajetórias de desbloqueio em produção nem imutabilidade
global contra outros clientes. Os desbloqueios continuam educacionais.

## Oito definições revisadas editorialmente

| ID publicado | Título | Descrição | Critério |
|---|---|---|---|
| `iQo20q4jxFnAtkJBn8F4` | Primeiro Passo | Adicionou a primeira transação. | `TRANSACTION_COUNT >= 1` |
| `aKJgBEffC6cTvrCaXjBY` | Mestre do Planejamento | Criou sua primeira meta financeira. | `GOAL_COUNT >= 1` |
| `Yf1Gf6ez5AkPUUECOePP` | Mestre da Sabedoria | Completou 3 lições de educação financeira. | `LESSON_COUNT >= 3` |
| `conquista_investimentos` | Primeiros passos | Conclua Introdução a Investimentos. | Todas as lições do módulo `investimentos` concluídas |
| `conquista_organizacao_financeira` | Base organizada | Conclua Orçamento Pessoal e Reserva de Emergência. | Todas as lições de `organizacao_financeira` concluídas |
| `streak_7` | Ritmo de aprendizado | Alcance uma sequência de 7 missões diárias concluídas. | Maior sequência histórica >= 7 |
| `streak_15` | Consistência | Alcance uma sequência de 15 missões diárias concluídas. | Maior sequência histórica >= 15 |
| `streak_30` | Hábito construído | Alcance uma sequência de 30 missões diárias concluídas. | Maior sequência histórica >= 30 |

As três primeiras são históricas e ficam sem alterações. Seus ícones vazios são
válidos como legado e devem ser preservados. As cinco novas usam `EmojiEvents`,
categoria `EDUCACAO`, valores 1/1/7/15/30 e referências de módulo já aprovadas.

O manifesto histórico continua em `scripts/achievement-catalog-approved.json`, com
`productionApproved=false` e `PENDING_OWNER_REVIEW`. Digest canônico:
`8bb55c5ebc0824729096a6dea059d81b0235bd51746670adff3ecbe716efaca8`.
Esses metadados históricos permanecem congelados para preservar H/I. O aceite
editorial está vinculado aos mesmos cinco conteúdos na proposta isolada. A
autorização específica da tentativa concluída foi registrada no Changelog 129;
os metadados congelados não são pendência de publicação nem autorização para
novas operações. Fixtures locais não aprovam outros conteúdos.

## Dados e autoridade

- Quantidades vêm dos documentos do próprio usuário em `transactions`, `goals`
  e `lessonProgress`; módulos usam as lições existentes do catálogo e seus IDs.
- A sequência é derivada dos documentos concluídos em `dailyMissionProgress`.
  Mantém-se a política histórica: até três dias completos perdidos são
  tolerados; um intervalo de cinco dias entre conclusões reinicia a sequência.
  Quatro dias completos sem missão zeram a sequência atual. As conquistas
  comparam o maior valor histórico, não a sequência atual.
- Datas inválidas ou posteriores ao dia de referência são ignoradas na
  projeção. O dia de referência continua sendo o relógio do dispositivo em
  `America/Sao_Paulo`; isso não fornece autoridade de servidor.
- Esses dados são persistidos, mas autodeclarados pelo usuário. Rules validam
  proprietário, formato e referências, não comprovam aprendizado, resposta
  correta, poupança bancária ou data real. Os desbloqueios são educacionais;
  não concedem XP persistido, dinheiro, acesso ou certificação.
- Histórico canônico e legado é conservado como recebido, sem presumir que
  registros antigos criados sob Rules mais amplas sejam provas confiáveis.

## Implementação e compatibilidade

`AchievementRepository` já lê as definições e calcula os fatos locais;
`deriveSparkAchievements` já suporta os seis tipos de critério.
`combineAchievements` junta por ID, preserva desbloqueios históricos e datas,
consolida duplicatas legadas e produz um card por definição. Não se persiste
`isUnlocked` ou um novo progresso canônico no Spark.

Atualizações de conquistas canceladas são descartadas, e o Perfil atualiza os
dados ao retomar a tela. O cálculo não depende das Functions históricas.
Nenhum novo critério de `MONTHLY_SAVINGS` está sendo proposto ou publicado.

O cabeçalho comum de Início, Transações e Aprender recebe `currentStreak` do
`DailyMissionViewModel` existente. Aprender usa o mesmo estado do modal;
Início/Transações usam o ViewModel do próprio destino e atualizam na retomada.
O contador reage ao estado confirmado e à virada de data já tratada pelo
ViewModel. Loading, erro, indisponibilidade, data antiga ou contagens inválidas
exibem `🔥 0`. Não há incremento otimista, gravação ou contador paralelo.

O título do aplicativo pode usar reticências em telas pequenas para preservar
o indicador, o nível, a barra de XP e o botão Perfil. O teste usa largura320dp.

## Histórico do procedimento de publicação concluído

Os passos abaixo registram a preparação e a execução encerradas nos Changelogs
128 e 129. Não são uma lista de operações pendentes.

1. O proprietário aprovou as cinco novas definições da matriz, incluindo a
   política de tolerância da sequência, e autorizou a tentativa final.
2. O inventário administrativo de `achievements`, módulos e dependências de
   learning confirmou o estado anterior, sem ler usuários. Foram conferidos
   projeto `granaxp-d7927`, banco `(default)`, identidade OAuth efetiva,
   permissões existentes e janela exclusiva.
3. A proposta congelada por digest conteve **somente cinco creates**:
   `achievements/conquista_investimentos`,
   `achievements/conquista_organizacao_financeira`, `achievements/streak_7`,
   `achievements/streak_15`, `achievements/streak_30`.
   Nenhum update/delete de histórico, usuário ou documento de controle.
4. A execução reutilizou a validação/planner existentes e o executor separado
   `scripts/achievement-publish.mjs`, limitado à proposta congelada. O executorH
   continua `BLOCKED_E69H`; o publisher learning/daily e os outros executores
   não foram ampliados ou desbloqueados.
5. A transação conferiu campos e versões das três históricas e das dependências,
   além da ausência dos cinco novos IDs. Os cinco creates exigiram ausência.
   O protocolo preservado recusa estado parcial ou conflito, sem sobrescrever
   ou completar silenciosamente; conteúdo já aplicado idêntico não gera writes.
6. Uma única tentativa atômica criou as cinco definições e retornou
   `PUBLISHED_VERIFIED`. A reconciliação independente confirmou as oito por
   campos e versões, com três históricas intactas. Não houve retry. O protocolo
   de erro ou commit incerto permanece documentado para referência: somente
   leitura e parada, sem repetição automática.

Nenhuma nova Rule, Function, permissão IAM ou alteração de billing é necessária
para mostrar as definições. Clientes podem lê-las pelas Rules atuais. A política
atual continua negando escritas cliente no catálogo e em conquistas canônicas.

## Prova local

Os testes cobrem oito critérios, limiares e recarga sem duplicação, preservação
de histórico, cancelamento e datas futuras, cabeçalho reativo e largura320dp.
Uma fixture dedicada `demo-granaxp` inclui os oito cards, duas duplicatas legadas
e trinta datas sintéticas; não representa um histórico real nem conteúdo
automaticamente aprovado. A integração exercita o Repository, o Perfil e o SDK
reais, comparando os documentos próprios antes/depois para provar ausência de
escritas de conquistas. Lições e missão são exercitadas em contas locais separadas.
