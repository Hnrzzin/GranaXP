# Publicação isolada das cinco conquistas Spark

Publicação concluída em 19/10/2026, conforme o Changelog 129: resultado
`PUBLISHED_VERIFIED` em `granaxp-d7927/(default)`. A única tentativa autorizada
aplicou cinco creates em um commit atômico, preservou campos e versões das três
conquistas históricas e teve as oito definições reconciliadas por leitor
independente. A proposta executada tem digest
`14f9e0d048437253ed600f3823bcc4fade58799fa707d511a98d2bc70c0613e5`.

**A publicação já foi concluída; não repetir.** A autorização foi consumida e
o journal original deve ser preservado. Os comandos abaixo são referências do
procedimento administrativo, não operações necessárias para o checkpoint Git.

## Resultado registrado e limites da verificação

A reconciliação independente do Changelog 129 confirmou oito definições, sem
extras ou órfãos: três históricas com campos e versões originais e cinco novas
com os conteúdos aprovados. A operação administrativa enviou somente os cinco
creates e não leu nem escreveu dados privados de usuários/progresso. A inspeção
no AVD confirmou os oito cards e o indicador de sequência, sem comprovar todas
as trajetórias de desbloqueio em produção. Não houve varredura de dados privados
nem prova de imutabilidade global contra outros clientes.

Evidências originais preservadas:

- Journal: `C:/IA/contexto/GranaXP_Conquistas_Publicacao_20261019/publication-execution.jsonl`.
- Reconciliação independente: `C:/IA/contexto/GranaXP_Conquistas_Execucao_20261019/reconciliation-result.json`.
- Relatório: `C:/IA/contexto/GranaXP_Conquistas_Execucao_20261019/RELATORIO.md`.

Esta revisão documental usa esses registros existentes; não executa nova
publicação, reconciliação remota ou consulta Firebase. Os desbloqueios continuam
projeções educacionais, sem autoridade de servidor.

## Proposta congelada

- Projeto `granaxp-d7927`, número `136652496724`.
- Banco `(default)`, UID `81c02a82-fb39-40a3-b0b8-82439c8ba232`, Standard/native,
  região `southamerica-east1`.
- Proposta completa externa:
  `C:/IA/contexto/GranaXP_Conquistas_Publicacao_20261019/achievement-proposal.json`.
- Digest canônico da proposta:
  `14f9e0d048437253ed600f3823bcc4fade58799fa707d511a98d2bc70c0613e5`.
- Digest do manifesto de conteúdo preservado:
  `8bb55c5ebc0824729096a6dea059d81b0235bd51746670adff3ecbe716efaca8`.

O manifesto H continua com seus metadados históricos `PENDING_OWNER_REVIEW` e
`productionApproved=false`, protegidos pelo digest original. Não foi alterado
para desbloquear H/I. O aceite editorial novo está vinculado ao conteúdo exato
na proposta isolada (`APPROVED_BY_OWNER`, `executionAuthorized=false`). Esses
valores retratam a preparação congelada. A autorização específica posterior e a
execução concluída estão registradas no Changelog 129; não é necessário editar
o manifesto ou a proposta para refletir esse evento nem repetir a operação.

| Caminho criado | Título | Tipo | Valor | Referência |
| --- | --- | --- | --- | --- |
| `achievements/conquista_investimentos` | Primeiros passos | MODULE_COMPLETION | 1 | investimentos |
| `achievements/conquista_organizacao_financeira` | Base organizada | MODULE_COMPLETION | 1 | organizacao_financeira |
| `achievements/streak_7` | Ritmo de aprendizado | STREAK | 7 | null |
| `achievements/streak_15` | Consistência | STREAK | 15 | null |
| `achievements/streak_30` | Hábito construído | STREAK | 30 | null |

Todos usam `icon=EmojiEvents`, `category=EDUCACAO` e as descrições literais
aprovadas em `docs/spark-achievements.md`. São exatamente cinco creates, sem
update/delete/mask/transform, cada um com `currentDocument.exists=false`.
Não há escrita em histórico, usuários, progresso, controle, learning ou daily.

## Implementação e gates

`scripts/achievement-publish.mjs` é separado de `catalog-publish.mjs`
(learning/daily), do leitor I e de H (`BLOCKED_E69H`). Reutiliza hash,
validação do conteúdo e decoder existentes; não acrescenta dependências.

Antes de qualquer rede, valida JSON sem getters, digest congelado, cinco
operações exatas, alvo e identidade esperada. Não aceita outro digest mesmo
que recalculado corretamente. Copia entradas antes de operações assíncronas.

OAuth usa somente o arquivo original `authorized_user` explicitamente indicado,
GoogleAuth e token/tokeninfo oficiais, sem ADC ou descoberta de credenciais.
O e-mail efetivo precisa coincidir com o principal da proposta, estar verificado,
ter scope cloud-platform e validade suficiente. O mesmo bearer é usado em todas
as chamadas, sem renovação durante o fluxo. Erros não imprimem credenciais,
respostas brutas SDK ou IDs de transação.

Metadados de projeto/banco são conferidos. `testIamPermissions` consulta:

- `resourcemanager.projects.get`;
- `datastore.databases.getMetadata` e `datastore.databases.get`;
- `datastore.entities.get` e `datastore.entities.list`;
- `datastore.entities.create` em preflight/publish.

Reconcile não exige a permissão create. Nenhuma permissão é concedida ou
alterada. Não é necessária a permissão update para o escopo lógico do executor;
o principal pode ter privilégios mais amplos. A contenção dos caminhos está
implementada no transporte do programa, não por redução do IAM da conta.

`preflight` é exclusivamente de leitura: metadados/IAM e transação read-only,
sem commit ou transação read-write. Enquadra o estado como READY somente com
os três históricos e as cinco dependências idênticos, inclusive versões, e
ausência dos cinco candidatos. `verify` não usa rede nem credenciais.

## Atomicidade, concorrência e recuperação: protocolo preservado

Os passos abaixo descrevem o protocolo administrativo utilizado. Sua execução
foi encerrada no Changelog 129; eles não constituem uma nova autorização nem
uma tarefa pendente.

1. Confirmar autorização final deste digest e janela administrativa exclusiva.
2. Validar identidade/metadados/IAM; snapshot somente de leitura.
3. Ler novamente, em uma transação read-write, as coleções `achievements`,
   `modules` e `lessons`. Rejeitar IDs extras, órfãos, duplicatas, tipos não
   suportados e paginação inválida; comparar conteúdos e versões aprovados.
4. Enviar **um único commit de cinco creates**, cada um exigindo ausência.
5. Reconciliar os oito documentos e as cinco dependências em nova transação
   somente de leitura. Novos documentos devem ter as versões retornadas pelo
   commit; históricos/dependências continuam com suas versões originais.

O commit REST aplica seus writes atomicamente e em ordem, conforme o
[contrato oficial](https://docs.cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.documents/commit).
A [listagem REST](https://docs.cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.documents/list)
aceita o seletor transaction para essas leituras. As simulações locais não
provam a semântica ou a contenção efetiva do serviço real. A janela exclusiva
é uma confirmação operacional; o programa não adquire um lock global e não
prova ausência de inserções administrativas concorrentes em outros caminhos.

Os cinco já presentes e idênticos, com histórico/dependências preservados,
resultam em ALREADY_APPLIED e zero writes. Parcial/conflito/divergência para
antes do commit; não completa silenciosamente nem sobrescreve nada.

Não há retry automático. Resposta perdida, erro HTTP ou resposta de commit
inválida produz COMMIT_UNCERTAIN, reconciliação somente de leitura e parada.
Qualquer falha posterior ao envio do commit também inclui estado observado
APPLIED/DIVERGED/UNKNOWN quando possível. APPLIED descreve conteúdo observado,
não prova autoria. Mesmo APPLIED após erro não autoriza nova tentativa.

O CLI exige journal externo novo (`wx`), escreve todos os bytes e executa
fsync antes de avançar. A API exportada exige callback de journal antes de
autenticar; o CLI fornece a implementação durável, e os testes usam um sink
sintético explícito. Falha do journal interrompe o fluxo. Os eventos terminais
de publicação ficam dentro do contexto que ainda consegue reconciliar o commit.
Preserve logs e transações incertas; nunca apague o journal para tentar de novo.

## Comando histórico de publicação — executado no Changelog 129

O comando abaixo registra a tentativa autorizada, após confirmação da janela
exclusiva. Foi executado uma única vez, com resultado `PUBLISHED_VERIFIED`.
Não o executar novamente para preparar ou publicar o checkpoint Git:

```powershell
$nodeFile = 'C:/Users/close/AppData/Local/Temp/granaxp-node22-runtime/node_modules/node/bin/node.exe'
$publisher = 'C:/Users/close/StudioProjects/GranaXP/scripts/achievement-publish.mjs'
$taskDir = 'C:/IA/contexto/GranaXP_Conquistas_Publicacao_20261019'
$proposalFile = "$taskDir/achievement-proposal.json"
$catalogPrincipal = (Get-Content -LiteralPath $proposalFile -Raw | ConvertFrom-Json).principal
$catalogCredential = Join-Path $env:APPDATA ('firebase/' + $catalogPrincipal.Replace('@','_').Replace('.','_') + '_application_default_credentials.json')
$common = @(
  '--proposal', $proposalFile,
  '--project', 'granaxp-d7927', '--database', '(default)',
  '--project-number', '136652496724',
  '--database-uid', '81c02a82-fb39-40a3-b0b8-82439c8ba232',
  '--principal', $catalogPrincipal, '--credential-file', $catalogCredential
)
& $nodeFile $publisher publish @common `
  --admin-window exclusive `
  --confirm 'PUBLISH_ACHIEVEMENTS/granaxp-d7927/(default)/14f9e0d048437253ed600f3823bcc4fade58799fa707d511a98d2bc70c0613e5' `
  --audit "$taskDir/publication-execution.jsonl"
```

O journal já existe e a tentativa foi consumida: parar, sem repetir o comando.
Não removê-lo nem trocar automaticamente seu nome para repetir writes.
Falta de runtime, credencial, IAM ou catálogo compatível
é bloqueio: não criar recursos ou mudar o digest para contornar o problema.

Referência de diagnóstico posterior, somente de leitura, com journal novo.
A reconciliação da publicação já foi concluída; este comando não é necessário
nesta etapa. Só usar diante de necessidade concreta e autorização própria:

```powershell
& $nodeFile $publisher reconcile @common --audit "$taskDir/publication-reconciliation.jsonl"
```

Validação offline (referência, sem nova publicação):

```powershell
& $nodeFile $publisher verify --proposal $proposalFile
$env:GRANAXP_ACHIEVEMENT_PROPOSAL = $proposalFile
& $nodeFile --test scripts/achievement-publish.test.mjs
```

Não fazer rollback destrutivo das definições, não alterar progresso e não
restaurar Rules permissivas. Recuperação após divergência é diagnóstico por
leitura e correção para a frente especificamente autorizada. Nenhuma Function,
mudança de Rules/IAM/APIs/billing ou nova escrita cliente é necessária para
apresentar as definições. Os desbloqueios continuam apenas educacionais.
