# Publicação controlada Spark — executor separado

Preparação autorizada em 16/10/2026; nenhuma escrita real autorizada nesta etapa.
H/BLOCKED_E69H e leitor I permanecem intactos.

## Desenho e plano

Criar somente scripts/catalog-publish.mjs, seu teste e este runbook.
Reutilizar hashes/validação/planos E6.6 e decodificação Firestore I, sem nova
dependência. Manter os dois digests da proposta118 fixos no executor.

1. Testes RED para propostas adulteradas, alvo/identidade/permissões, wire
   writes, sequência 40→reconciliação→7, concorrência, falha parcial e
   resultado incerto. Transporte simulado e sockets bloqueados.
2. Implementar o executor REST separado: autenticação GoogleAuth, mesmo
   bearer verificado em tokeninfo, metadados/IAM, transações fechadas e
   reconciliação. Nenhum acesso real durante desenvolvimento/testes.
3. Rodar testes/regressões pertinentes, revisão independente e correções
   necessárias; documentar o comando exato, atualizar HANDOFF e parar.

Restrições: granaxp-d7927/(default), principal da proposta, apenas dois
módulos/34blocos/controle e três updates de lessons; daily sete creates.
Nenhum usuário/progresso/achievement/Rules/Functions/IAM/API/billing é
alterado. Nenhum commit/push ou mudança Git. A autorização expressa de
implementar/testar prevalece sobre pausas genéricas adicionais de skill.

Revisão deve verificar especialmente: campo extra que escape do digest,
substituição de token/principal, bypass por endpoint/redirect, perda de
campo histórico no updateMask, mudança entre reconciliação e daily e
segunda aplicação após perda da resposta do commit.

## Contrato fechado

Alvo exclusivo: projeto `granaxp-d7927`, número `136652496724`, banco
`(default)`, UID `81c02a82-fb39-40a3-b0b8-82439c8ba232`, Native/Standard,
`southamerica-east1`. O principal deve coincidir com ambos os arquivos completos
da proposta118. O executor recalcula os envelopes completos e os planos E6.6;
um novo digest recalculado após adulteração também é rejeitado pelos pins.

| Escopo | Digest canônico da proposta completa | Escritas |
| --- | --- | --- |
| learning | `0f00b4000eda98a3a7c6d3a6d524fa9347eadc811364dc72bc66746ae07e9d7e` | 37 creates + 3 updates |
| daily | `35e7b55e338965e3bfee1a8454513aaafe8f6fd54cd57821b0b7c8b5c6ef2b6a` | 7 creates |

Os digests são SHA-256 do JSON canônico do envelope sem seu campo digest,
conforme `educational-publication.mjs`; não são o hash bruto do arquivo nem o
digest do plano E6.6 interno. Bundle aprovado: hash canônico
`01353e49cfa38b61c791eaad1e75ad8c6f34ae949f3e9aae7bb1fff9c4fe99ae`.

Learning permite apenas:

- Criar `modules/organizacao_financeira` e `modules/investimentos`.
- Atualizar as lessons `2iT6j1pA0vvq62oX7jO7`, `gMmIv1XNtMhIrhBzDFKc` e
  `Eo6rq9c4HDAtZRR6TAjp`, exclusivamente `description`, `duration`, `moduleId`,
  `order`, `title` e `xpReward`. São metadados de catálogo, sem XP de usuário.
- Criar os 34 IDs de blocks exatos do bundle/proposta: 11 `orcamento_*`,
  12 `reserva_*`, 11 `investimentos_*`, nas três lessons acima. Prefixos não
  são autorização por wildcard: cada caminho e conteúdo é fixo pelo digest.
- Criar `learningCatalogControl/current`, protocolo1/geração2, no mesmo commit.

Daily permite criar somente `dailyMissions/` com estes sete IDs:
`dm_orcamento_01`, `dm_prioridades_02`, `dm_reserva_03`, `dm_meta_04`,
`dm_comparacao_05`, `dm_risco_06`, `dm_registro_07`.

Cada create usa `currentDocument.exists=false`. Cada update legado usa
`updateMask` dos seis campos e `currentDocument.updateTime` original, com
nanosegundos preservados. Os três IDs, `category`, `videoUrl` e quaisquer
campos extras existentes são mantidos; qualquer diferença inicial de conteúdo
ou versão em relação à proposta faz parar. Nenhuma exclusão, transform,
upsert, conquista, usuário, progresso ou dado financeiro pode ser enviado.
Não há comando para publicar daily isoladamente ou continuar parcialmente.

## Identidade, permissões e transporte

Credencial `authorized_user` explícita, arquivo absoluto existente; não usa
ADC, token da CLI, service account ou descoberta automática. GoogleAuth11.1.0
obtém token e tokeninfo verifica email esperado/verificado, scope
`cloud-platform` e validade. O mesmo bearer executa todas as chamadas REST;
não é renovado durante a publicação. Identidade, número/ID do projeto e
UID/tipo/edição/localização do banco são verificados antes das transações.
Erros não imprimem tokens, payloads de credencial, causas SDK ou transaction IDs.

`projects:testIamPermissions` consulta, sem alterar políticas:

| Permissão | Uso |
| --- | --- |
| `resourcemanager.projects.get` | Metadados do projeto |
| `datastore.databases.getMetadata` | Identificação do banco |
| `datastore.databases.get` | Begin/rollback de transações |
| `datastore.entities.get` | Documento de controle e leituras |
| `datastore.entities.list` | Inventários de catálogo |
| `datastore.entities.create` | Creates aprovados, somente publish |
| `datastore.entities.update` | Updates aprovados, somente publish |

Reconcile exige as cinco primeiras; publish exige as sete. Ausência ou recusa
da API faz parar, sem alterar IAM/APIs. A etapa118 só confirmou quatro
permissões de leitura; as três adicionais acima ainda não foram conferidas
no Firebase real nesta preparação. A política IAM da conta pode ser mais ampla;
a restrição aos caminhos/conteúdos é implementada pelo programa.

OAuth permite só POST token/tokeninfo oficiais. Transporte de recursos permite
só os dois GET de metadados, POST testIamPermissions, beginTransaction,
rollback e os dois commits com corpos exatos. Leituras de catálogo limitam-se
a modules, lessons, blocks das três lessons, dailyMissions e GET exato do
controle. Não há consulta a achievements nem a dados privados. Proxy/ADC/
emulador/universe overrides são recusados; redirects e retry estão desativados.
Cada request tem timeout15s; commit exige transação com menos de120s.

## Ordem, concorrência e interrupção

1. Validar arquivos completos, bundle, pins, alvo, confirmação e janela Admin
   exclusiva; copiar os dados antes de qualquer operação assíncrona.
2. Validar token, metadados e permissões existentes.
3. Fazer snapshot learning+daily numa transação read-only. Enumerar todas as
   páginas com `showMissing=true`, rejeitando pais órfãos, IDs inesperados,
   duplicatas, valores não suportados e tokens de paginação repetidos.
4. Abrir transação read-write learning; reler catálogo e versões originais;
   enviar um único commit40. Qualquer precondição/conflito interrompe tudo.
5. Novo snapshot read-only: learning precisa corresponder ao conteúdo esperado
   e às versões retornadas pelo commit; daily precisa continuar intacto.
6. Abrir transação read-write daily; reler daily e **todo learning na mesma
   transação**, com conteúdo e versões idênticos à reconciliação; commit7.
7. Novo snapshot read-only: confirmar ambos os conteúdos, versões de daily
   retornadas pelo commit e versões learning inalteradas.

Os 40 writes learning são atômicos; os sete daily também. O conjunto47 não é
uma transação única: learning permanece aplicado se daily não puder concluir.
O [contrato REST de commit](https://docs.cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.documents/commit)
define atomicidade/ordem e correspondência dos writeResults. A
[listagem REST](https://docs.cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.documents/list)
permite leitura com transaction e paginação. A
[documentação de contenção](https://docs.cloud.google.com/firestore/native/docs/transaction-data-contention)
descreve isolamento; os testes simulados não comprovam esses mecanismos reais.

Não há retry automático de commit/transação. Resposta perdida, HTTP de erro,
JSON inválido ou quantidade/timestamp inválido dos resultados levam a
`COMMIT_UNCERTAIN`, reconciliação somente leitura e parada, mesmo que se observe
APPLIED. Se a leitura não for possível, informa UNKNOWN. APPLIED descreve o
conteúdo observado, não prova autoria do write.

O journal externo é criado sem sobrescrita, registra eventos sem credenciais,
completa escritas parciais e executa fsync antes de prosseguir. Falha em evento
interrompe; após commit enviado, `JOURNAL_FAILED` inclui reconciliação observed.
Transações não enviadas recebem rollback; falha de cleanup impede avanço ou
registra ROLLBACK_UNCONFIRMED. Crash/timeout ainda pode deixar transação vencer
no servidor ou journal incompleto; isso nunca autoriza repetir writes.

Janela exclusiva é uma confirmação operacional, não um lock adquirido pelo
script. Não operar Console/outro Admin durante execução. A simulação verifica
versões dos documentos lidos, sem modelar phantom/range locks. A reconciliação
rejeita documento inesperado, mas a preparação não demonstra que toda inserção
Admin concorrente seria impedida antes do commit. Não se inventaria o banco
inteiro ou subcoleções arbitrárias; a garantia limita-se aos caminhos aprovados.

## Comandos preparados — publicação NÃO executada

Executar somente após autorização final explícita dos dois digests e confirmação
da janela exclusiva. Nenhuma configuração permanente de shell é necessária.
Não habilitar proxy/emulador/ADC nem exibir o conteúdo da credencial.

```powershell
$nodeFile = 'C:\Users\close\AppData\Local\Temp\granaxp-node22-runtime\node_modules\node\bin\node.exe'
$publisher = 'C:\Users\close\StudioProjects\GranaXP\scripts\catalog-publish.mjs'
$proposalDir = 'C:\IA\contexto\GranaXP_Spark_Prepublicacao_2026-10-16_202803'
$bundleFile = 'C:\IA\contexto\GranaXP_E6_6_2026-10-14\catalog-bundle.private.json'
$principal = (Get-Content -LiteralPath "$proposalDir\learning-proposal.private.json" -Raw | ConvertFrom-Json).principal
$credential = Join-Path $env:APPDATA ('firebase\' + $principal.Replace('@','_').Replace('.','_') + '_application_default_credentials.json')
$common = @(
  '--bundle', $bundleFile,
  '--learning', "$proposalDir\learning-proposal.private.json",
  '--daily', "$proposalDir\daily-proposal.private.json",
  '--project', 'granaxp-d7927', '--database', '(default)',
  '--project-number', '136652496724',
  '--database-uid', '81c02a82-fb39-40a3-b0b8-82439c8ba232',
  '--principal', $principal, '--credential-file', $credential
)
& $nodeFile $publisher publish @common `
  --admin-window exclusive `
  --confirm 'PUBLISH/granaxp-d7927/(default)/0f00b4000eda98a3a7c6d3a6d524fa9347eadc811364dc72bc66746ae07e9d7e/35e7b55e338965e3bfee1a8454513aaafe8f6fd54cd57821b0b7c8b5c6ef2b6a' `
  --audit "$proposalDir\publication-execution.jsonl"
```

Esse comando corresponde a uma tentativa controlada learning→reconciliação→daily.
Se journal já existir, parar e preservar a evidência; não apagar para repetir.
O caminho da credencial original foi identificado no117; não se cria cópia.
O runtime Node22 temporário e functions/node_modules precisam continuar
disponíveis. Se faltar credencial/runtime/dependência, parar sem criar recursos
reais. O comando não usa firebase deploy.

Verificação posterior ou diagnóstico de resultado incerto, somente leitura,
usando as mesmas variáveis `$common` acima e arquivo de audit ainda inexistente:

```powershell
& $nodeFile $publisher reconcile @common --audit "$proposalDir\publication-reconciliation.jsonl"
```

Este reconcile também NÃO foi executado contra produção nesta preparação.
Se ambos APPLIED: nenhuma reaplicação; uma chamada publish posterior retorna
ALREADY_APPLIED sem writes. Learning APPLIED/daily UNCHANGED resulta em
RESUME_REQUIRES_REVIEW: preservar learning, obter inventário atualizado e
decidir recuperação para a frente com autorização específica. Não excluir
catálogo nem restaurar Rules permissivas; não mudar pins para contornar erro.
DIVERGED/UNKNOWN exige diagnóstico e interrupção. Smoke real do APK, contas e
escritas de usuário seguem fora desta autorização de preparação/publicação.

Verificação offline dos arquivos, sem identidade/rede:

```powershell
& $nodeFile $publisher verify --bundle $bundleFile `
  --learning "$proposalDir\learning-proposal.private.json" `
  --daily "$proposalDir\daily-proposal.private.json"
```

## Testes e limites da evidência

Suíte nova usa GoogleAuth real com OAuth sintético e transporte Firestore
simulado; sockets/TLS/fetch externos bloqueados. Verifica40→reconciliação→7,
allowlist/digests/identidade/IAM, preservação de extras, versões concorrentes,
commit perdido/malformado, paginação, rollback, journal antes/depois de commits,
short writes e impedimento de reaplicação parcial. Não executa credencial real,
emulador, Android ou Firebase real. Não prova a identidade/IAM atuais nem a
aceitação do protocolo pelo serviço real; os gates reais ocorrerão antes de
qualquer write na execução autorizada e param se divergir.

Revisão independente identificou duas falhas importantes de journal e getter
raiz menor. Regressões RED→GREEN corrigiram os três; cobertura adicional de
paginação, replies malformados e rollback foi incluída. O limite de phantoms foi
documentado, sem alegar que o fake prove isolamento do Firestore.

Rodada final em16/10/2026: **67/67**, zero falhas/skips/cancelamentos:
25 publisher novo +18 leitor I +8 E6.6 +12 H +4 conquistas. CLI `verify`
revalidou os arquivos completos e os dois digests com zero tentativas de rede;
`productionExecuted=false`. Sintaxe dos dois arquivos novos aprovada.
Logs: `C:/IA/contexto/GranaXP_Spark_Executor_20261016_204824/`.

Reprodução dos testes offline, a partir da raiz do checkout:

```powershell
$env:GRANAXP_SPARK_PROPOSAL_DIR = 'C:\IA\contexto\GranaXP_Spark_Prepublicacao_2026-10-16_202803'
$env:GRANAXP_APPROVED_BUNDLE = 'C:\IA\contexto\GranaXP_E6_6_2026-10-14\catalog-bundle.private.json'
& $nodeFile --test --test-concurrency=1 scripts/catalog-publish.test.mjs scripts/catalog-readonly.test.mjs scripts/educational-publication.test.mjs scripts/catalog-admin.test.mjs scripts/achievement-publication.test.mjs
```
