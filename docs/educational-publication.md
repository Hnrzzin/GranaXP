# Publicação educacional — preparação E6.6

## E6.9H — executor administrativo implementado somente para ensaio local

O novo `scripts/catalog-admin.mjs` reutiliza `createPlan`, validação, hashes e
`readScope` E6.6. O executor E6.6 permanece intacto e bloqueando produção.
O novo executor também mantém uma barreira **incondicional**:
`BLOCKED_E69H`. Nenhum argumento, digest ou mudança no manifesto libera conexão
real. Snapshot, aplicação e reconciliação só abrem SDK para `demo-granaxp`,
banco `(default)`, nos loopbacks dedicados 18080/28080. Preview de produção é
uma operação puramente offline; os metadados informados não são verificação real.

Esta implementação precisa de auditoria separada. Não é autorização de
publicação nem comprovação de permissões, identidade ou comportamento real.
E6.9H não consulta nem mesmo metadados do Firebase real.

### Escopos, preservação e aprovação

| Escopo | Estado inicial admitido | Operação |
|---|---|---|
| learning | Três lições iguais à baseline legada aprovada | 37 criações + três updates, incluindo controle no mesmo commit |
| daily | Learning completo e sete missões ausentes | Sete criações, transação e aprovação separadas |
| achievements | Learning completo e três definições históricas preservadas | Cinco criações locais; oito definições ao final |

Conteúdo já publicado e idêntico gera zero escritas. Catálogo parcialmente
preenchido, documento extra, conteúdo divergente, campo desconhecido, tipo
Firestore não representável pelo contrato JSON ou lição histórica ausente
interrompem a operação. Não existe delete, upsert, scope all ou migração de
usuários/progresso/XP. Os três IDs de lição e campos aprovados `category` e
`videoUrl` são mantidos. As três definições históricas não recebem update.

O envelope inclui contexto explícito (projeto, banco, ambiente e principal),
escopo, snapshot completo com `updateTime` em nanossegundos, dependências,
operações E6.6, hash da lista de recursos e digest do manifesto de conquistas
quando aplicável. Seu digest cobre o envelope inteiro. A aplicação exige
`--digest` exato e `--confirm` no formato impresso pelo preview:
`projeto/banco/escopo/digest/resourcesHash`.
Alterar arquivo, contexto, escopo, payload ou snapshot invalida a confirmação.
O processo consome a cópia validada em memória, sem reler o plano no callback.

Todas as leituras transacionais precedem writes. O callback revalida versões,
dependências e digest em cada tentativa do SDK, limitada a três tentativas.
Uma versão diferente aborta, mesmo com campos finais iguais. Replay do plano
antigo é recusado; para no-op, obter snapshot e plano novos.
Não há loop externo de aplicação. Resposta perdida após commit provoca
releitura: estado final integral correspondente pode ser confirmado sem segunda
aplicação; ausência ou divergência permanece erro/incerteza. A conferência inclui
versões dos documentos históricos que a operação não deveria escrever.

Cada escopo é atômico; os três escopos não formam uma única transação. Se daily
falhar após learning, preservar learning e interromper. Recuperação é para a
frente, por plano novo e autorização própria, sem apagar histórico, reduzir XP,
trocar IDs ou restaurar Rules permissivas.

O scan de pais ausentes detecta blocks órfãos inesperados, mas não constitui
lock global contra Console/Admin fora do protocolo. Uma futura execução real
exigirá inventário novo, janela exclusiva de operadores e conferência posterior.
Nenhum plano derivado das fotografias D/G substitui esse inventário.

### Identidade e barreira de conexão

A sessão privada vincula o cliente SDK à mesma instância GoogleAuth cuja
`getCredentials()` informa o principal esperado. Não consulta login, aliases
ou conta do Firebase CLI. O cliente e a sessão são fixados em
`demo-granaxp/(default)`, host literal e SSL desativado; cada operação verifica
novamente ambiente, destino e vínculo da identidade antes de acessar documentos.

Somente a identidade fictícia `catalog-admin@example.test` e material fictício
embutido no adaptador local são usados. Isso prova o vínculo local de configuração,
não autorização IAM nem autenticação em produção. O transporte inseguro do
emulador não autentica uma service account real. ADC, tokens/proxies/endpoints
ambientais inesperados são recusados. O SDK só é carregado depois dos gates.
A implementação verifica internals `_settings` do Firestore instalado; atualização
da dependência exige reexecutar esse gate.

### Manifesto de conquistas: candidato editorial

`scripts/achievement-catalog-approved.json` contém a transcrição candidata dos
cinco designs citados na E6.9G, com origem, critérios, compatibilidade e digest.
O nome histórico “approved” não concede aprovação de produção:
`editorialApproval.status=PENDING_OWNER_REVIEW` e
`productionApproved=false`. O código fixa o digest candidato; editá-lo para
true ou alterar conteúdo não libera publicação.

Digest do manifesto candidato:
`8bb55c5ebc0824729096a6dea059d81b0235bd51746670adff3ecbe716efaca8`.
O hash da lista de definições de referência continua
`dd1a430e0f96c606b22d08a585b5c64e0a5c4ae4879f2c515106843695492dac`.
São objetos diferentes; nenhum é o hash do bundle learning/daily E6.6.

IDs novos: `conquista_investimentos`, `conquista_organizacao_financeira`,
`streak_7`, `streak_15`, `streak_30`. Preservados sem alteração:
`Yf1Gf6ez5AkPUUECOePP`, `aKJgBEffC6cTvrCaXjBY`,
`iQo20q4jxFnAtkJBn8F4`. Referências dos módulos e limiares 7/15/30 são
verificados. A UI atual consulta todas as definições: três históricas mais cinco
novas resultam em oito cards por inspeção do código. Não houve mudança ou novo
ensaio Android nesta H; a decisão editorial sobre oito cards permanece pendente.

### Uso exclusivamente local

Com Firestore **novo, vazio e dedicado**, regras vigentes e ambiente sem
credenciais/proxies reais, usar Node 22. Não reutilizar a Suite histórica nem
importar contas/dados reais. Exemplos abaixo são comandos locais, não deploy:

```powershell
$nodeFile = 'C:\Users\close\AppData\Local\Temp\granaxp-node22-runtime\node_modules\node\bin\node.exe'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:28080'
$base = @('--project','demo-granaxp','--database','(default)',
  '--environment','emulator','--host','127.0.0.1:28080',
  '--principal','catalog-admin@example.test','--scope','learning')
& $nodeFile scripts/catalog-admin.mjs snapshot @base --out '<caminho absoluto externo novo para snapshot>'
& $nodeFile scripts/catalog-admin.mjs preview @base --bundle '<bundle E6.6 aprovado>' --snapshot '<snapshot>' --out '<plano privado novo>'
& $nodeFile scripts/catalog-admin.mjs apply @base --bundle '<bundle>' --plan '<plano>' --digest '<digest exato>' --confirm '<confirmação exata impressa>'
& $nodeFile scripts/catalog-admin.mjs reconcile @base --bundle '<bundle>' --plan '<plano>'
```

O cenário learning exige as três fixtures sintéticas legadas antes do snapshot;
os testes as preparam. Para daily/achievements, learning precisa estar completo.
Trocar o escopo exige snapshot, plano, digest e confirmação próprios.
Saídas são externas ao repositório, com criação exclusiva e modo 0600; no Windows,
verificar ACLs do diretório administrativo. Erros de CLI são códigos sanitizados,
sem stack, token ou conteúdo privado.

Testes do executor novo, com a mesma instância local dedicada:

```powershell
$env:GRANAXP_APPROVED_BUNDLE = '<cópia local do bundle E6.6 aprovado>'
$env:GRANAXP_ADMIN_INTEGRATION = '1'
& $nodeFile --test --test-concurrency=1 scripts/achievement-publication.test.mjs scripts/catalog-admin.test.mjs scripts/catalog-admin-emulator.test.mjs
```

A integração exige opt-in, bloqueia conexões para qualquer endereço/porta além do
loopback dedicado e apaga somente o banco sintético desse emulador exclusivo entre
cenários. Não executar contra uma Suite compartilhada.
Reexecutar também os testes históricos E6.6 e Rules conforme os gates do HANDOFF.
Não usar o runner histórico de Functions para estes testes.

### Gates ainda não atendidos para produção

Auditoria separada da H; aprovação editorial das cinco definições e dos oito
cards; autorização própria para uma futura implementação de conexão real;
identidade efetiva do SDK e permissões mínimas verificadas; inventário novo completo
de catálogo/control/definições; janela administrativa; autorização por digest para
learning, depois daily e achievements separadamente. Nenhuma habilitação de API,
alteração IAM, billing, credencial real ou criação de recurso está incluída.
As Rules publicadas na F e reconfirmadas na G não foram consultadas novamente na H.
Smoke real e distribuição continuam etapas próprias.

> **Atualização E6.9C (15/10/2026):** a V1 Spark não utiliza Functions nem
> HMAC. O publicador e os hashes E6.6 continuam sendo a fonte aprovada de
> conteúdo; o executor disponível permanece exclusivamente para emulador
> `demo-granaxp`, nos loopbacks dedicados `127.0.0.1:18080` e
> `127.0.0.1:28080` (testes de integração).
> Uma futura publicação de `blocks` e `dailyMissions` exporá
> `correctAnswerIds` a qualquer usuário autenticado pelas Rules Spark.
> Essa é uma limitação consciente da gamificação acadêmica, não um segredo
> protegido. `xpReward` serve apenas à experiência visual. Consulte
> [spark-v1.md](spark-v1.md). O plano de ativação com Functions mais abaixo
> é histórico e não vale para a V1 Spark.

## Limite desta entrega

Ferramenta administrativa de preparação e ensaio local. Não é componente Android, endpoint HTTP ou painel administrativo. Nenhum gabarito entra no APK. O executor disponível recusa produção antes de inicializar o SDK; não existe comando de publicação real. Uma futura execução real exige auditoria, autorização específica e adaptador com verificação dos gates de produção. Não remover o bloqueio apenas para fazer o Run funcionar.

O projeto normal continua usando `granaxp-d7927`. Escolher um AVD da Demo não altera esse destino. O inventário E6.5 é evidência datada: não confundir a pré-visualização offline com uma consulta ou validação atual de produção.

## Conteúdo e contrato

`scripts/educational-catalog-approved.json` fixa os caminhos e hashes canônicos dos payloads aprovados. O pacote privado contém somente `version`, `learning`, `daily`, `legacy`; permanece fora do repositório. Não é export Auth/Firestore da Demo.

| Escopo | Recursos | Operações esperadas a partir do E6.5 |
|---|---|---|
| learning | 2 módulos + 3 lições + 34 blocos + controle | 37 criações e 3 atualizações, numa transação |
| daily | 7 definições de missão | 7 criações, numa transação separada |

As três atualizações preservam IDs e campos legados. Nenhuma operação aponta para `users`, respostas/progresso, XP de usuário ou conquistas. `xpReward` é metadado da lição, não concessão de XP. Não há exclusão automática.

| Lição | moduleId | order | duration | xpReward |
|---|---|---|---|---|
| 2iT6j1pA0vvq62oX7jO7 | organizacao_financeira | 0 | 8 | 50 |
| gMmIv1XNtMhIrhBzDFKc | organizacao_financeira | 1 | 9 | 100 |
| Eo6rq9c4HDAtZRR6TAjp | investimentos | 0 | 10 | 150 |

Os 34 blocos distribuem-se em 11/12/11; nove são atividades. Módulos: Organização Financeira (ordem 0) e Investimentos (ordem 1). Missões, ordens 0–6: dm_orcamento_01, dm_prioridades_02, dm_reserva_03, dm_meta_04, dm_comparacao_05, dm_risco_06, dm_registro_07. O manifesto enumera cada path e hash, sem valores de respostas.

O bootstrap do controle ocorre junto das lições, sem estado intermediário: `{protocolVersion:1,generation:2}`. Com controle válido existente, a geração avança uma vez por mutação efetiva. Replanejamento de conteúdo já publicado gera zero escritas e mantém a geração. Missões não alteram o controle de lições, pois as callables diárias não o consomem.

O helper anterior `functions/src/learningCatalogPublication.ts` continua intacto. Seu contrato de geração é preservado; ele não permite bootstrap nem dailyMissions. O executor novo faz bootstrap/verificação/aplicação na mesma transação, em vez de separar essas operações em dois commits.

## Preparação e pré-visualização sem rede

Requisitos: Node 22 e dependências já instaladas. Todos os arquivos privados de saída exigem caminho absoluto externo ao repositório, diretório existente e nome ainda não utilizado. A CLI resolve links do diretório antes de verificar o destino e usa criação exclusiva. Guarde os arquivos em armazenamento administrativo protegido; a opção POSIX de modo de arquivo não substitui ACLs do Windows.

Exemplo PowerShell nesta máquina:

```powershell
$nodeFile = 'C:\Users\close\AppData\Local\Temp\granaxp-node22-runtime\node_modules\node\bin\node.exe'
$evidenceDir = 'C:\IA\contexto\GranaXP_E6_6_2026-10-14'
& $nodeFile scripts/educational-publication-cli.mjs prepare --drafts 'C:\IA\contexto\GranaXP_E1_2026-10-12\catalogo-aprovado' --missions 'C:\IA\contexto\GranaXP_E1_2026-10-12\e2\editorial-integrado.json' --out "$evidenceDir\bundle-new.private.json"
& $nodeFile scripts/educational-publication-cli.mjs preview --bundle "$evidenceDir\bundle-new.private.json" --snapshot "$evidenceDir\e65-catalog-snapshot.private.json" --project granaxp-d7927 --environment production --scope learning --out "$evidenceDir\learning-new-plan.private.json"
& $nodeFile scripts/educational-publication-cli.mjs preview --bundle "$evidenceDir\bundle-new.private.json" --snapshot "$evidenceDir\e65-catalog-snapshot.private.json" --project granaxp-d7927 --environment production --scope daily --out "$evidenceDir\daily-new-plan.private.json"
```

Esses três comandos não inicializam Firebase nem consultam credenciais. Os nomes `*-new*` são exemplos: não sobrescreva evidências existentes. O stdout da pré-visualização mostra paths, operações, nomes públicos de campos, hashes e confirmações. O plano salvo contém payloads privados e estado anterior; não publique esse arquivo.

Snapshot de entrada: `{documents:[{path,fields,updateTime?}]}`. O snapshot E6.5 usado no ensaio foi derivado somente dos documentos educacionais consultados anteriormente, sem usuários. Para uma execução futura, é obrigatório obter inventário novo autorizado e completo do escopo; não aplicar uma fotografia antiga.

## Ensaio com escrita exclusivamente local

Use uma instância nova de Firestore, `demo-granaxp`, `127.0.0.1:18080` (WebSocket 18081), sem importar contas ou dados de AVDs/Suites existentes. Não reutilize a Suite histórica na 8080. O processo de teste precisa ter ambiente de credenciais limpo. O JAR usado no E6.6 foi Firestore Emulator 1.22.0, com JVM `-Duser.language=en -Duser.country=US`; a execução pt_BR falhou em recursos de validação. Não alterar o sistema para esse ajuste.

Com o emulador dedicado já iniciado e verificado:

```powershell
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:18080'
& $nodeFile scripts/educational-publication-cli.mjs snapshot-emulator --project demo-granaxp --environment emulator --scope learning --host 127.0.0.1:18080 --out "$evidenceDir\local-snapshot-new.private.json"
& $nodeFile scripts/educational-publication-cli.mjs preview --bundle "$evidenceDir\bundle-new.private.json" --snapshot "$evidenceDir\local-snapshot-new.private.json" --project demo-granaxp --environment emulator --scope learning --out "$evidenceDir\local-plan-new.private.json"
```

Após revisar a lista, use `apply-emulator` com os mesmos `--bundle`, `--plan`, `--project demo-granaxp`, `--environment emulator`, `--scope learning`, `--host 127.0.0.1:18080`, além de `--confirm` e `--resources-hash` exatamente como impressos na pré-visualização. Não usar confirmação genérica como `yes`. Para missões, gerar snapshot e plano próprios com `--scope daily`.

O executor revalida o conteúdo/escopo/digest, lê o estado dentro da transação e recusa divergências antes de escrever. Replay de plano antigo é recusado; novo snapshot/plano do mesmo conteúdo é idempotente. Documentos de catálogo não previstos ou payloads existentes diferentes interrompem a aplicação.

O inventário também lista referências de `lessons`, incluindo pais ausentes com subcoleções, para detectar blocos órfãos fora das três lições. Essa listagem de metadados não é um lock transacional global. A coordenação pressupõe que todos os escritores administrativos respeitem o protocolo e a janela exclusiva; IAM/Console com acesso Admin pode contornar Rules e esse protocolo. Nenhum teste local prova ausência de escritores externos em produção.

## Testes e guard de acesso

```powershell
$env:GRANAXP_APPROVED_BUNDLE = "$evidenceDir\prepared.private.json"
$env:GRANAXP_PUBLICATION_INTEGRATION = '1'
$env:GCLOUD_PROJECT = 'demo-granaxp'
$env:GOOGLE_CLOUD_PROJECT = 'demo-granaxp'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:19099'
& $nodeFile functions/node_modules/typescript/bin/tsc -p functions/tsconfig.json
& $nodeFile --test scripts/educational-publication.test.mjs scripts/educational-publication-emulator.test.mjs
& $nodeFile --test scripts/backend-access-audit.test.mjs
```

Os testes de integração exigem emulador vazio e exclusivo. Criam somente fixtures novas locais, incluindo um documento sentinela para comprovar que a publicação não o altera. O teste de Rules usa perfil válido e controle positivo de edição de nome antes de negar XP. O teste de guard não executa operações reais de negócio; testa 63 negativas nas nove funções e duas admissões de `openDailyMission` em data anterior ao ciclo, sem Firebase Auth remoto.

| Modo | Efeito sobre as nove callables |
|---|---|
| disabled, ausente ou inválido | Bloqueia autenticados; não inicia operação protegida |
| pilot com UID válido | Admite somente o UID configurado; demais recebem permission-denied |
| pilot sem UID válido | Falha fechada |
| open | Admite qualquer UID autenticado; não dispensa autenticação nem invariantes de negócio |

Desde a correção local E6.9A, o guard usa o mapa fechado `BACKEND_CALLABLE_MODES`, com modo individual para as nove callables, e `BACKEND_PILOT_UID` server-side. Todos os modos exigem autenticação; o antigo `BACKEND_ACCESS_MODE` não autoriza mais chamadas. Defaults individuais são `disabled`. A matriz proposta, os três gatilhos internos de conquistas e a autorização privada de200XP estão em [backend-security-v1.md](backend-security-v1.md); nada disso foi publicado pela preparação local. Metas/exclusão não devem herdar o piloto educacional indiscriminadamente. O runner principal continua bloqueado e sua configuração antiga não é suficiente para ensaiar o mapa novo.

## Histórico de ativação com Functions — substituído pelo contrato Spark

1. Auditar este diff, validar payloads/manifesto e aprovar mecanismo real de execução. Parar diante de qualquer divergência.
2. Autorizar novo inventário real, inclusive avaliação delimitada de dados legados/marcadores, escritores e clientes antigos. Nenhuma leitura de usuários ocorreu no E6.6.
3. Aprovar janela e política de acesso das nove funções. Verificar billing/APIs/IAM e separar operador, runtime e publicador. O 403 do Secret Manager no E6.5 não prova inexistência de segredo.
4. Autorizar provisionamento de segredo real próprio `LESSON_ACTIVITY_PROOF_KEYRING`, com IDs de chave e chaves de 32 bytes em base64 canônico. Não reutilizar a chave fictícia. Não guardar valores no APK, plano, relatório ou Git.
5. Autorizar deploy das nove Functions contidas; verificar projeto, região southamerica-east1, runtime, nomes, parâmetros e vínculos do segredo. Testar sem ativação geral conforme autorização própria.
6. Autorizar publicação das Rules restritivas e verificar negativas via cliente antes de inserir qualquer gabarito. Coordenar o impacto em metas, XP e exclusão de conta. Rejeitar clientes/writers antigos incompatíveis.
7. Autorizar operação de catálogo learning: snapshot novo, plano revisado, confirmação explícita e transação de 40 operações esperadas. Verificar IDs, campos preservados, conteúdo, controle e hashes; parar antes de daily em caso de erro.
8. Autorizar operação daily separada: catálogo de sete missões, ordens 0–6 e IDs estáveis. Não alterar o ciclo silenciosamente depois de progresso diário existir.
9. Repetir o inventário delimitado de marcadores e campos legados após Rules restritivas e contenção dos escritores, antes de confiar em provas/histórico ou abrir piloto. Parar em anomalias, sem reconciliação automática. Autorizar piloto autenticado real e ensaio Android normal: módulos 2+1, lições/atividades, missão, persistência, repetição/concorrência sem XP duplicado, negativas de segurança e regressão financeira. Somente então decidir ampliação de acesso.

Nenhuma etapa acima está executada ou implicitamente autorizada. As cinco callables educacionais isoladas não cobrem o impacto das Rules integrais sobre o restante da V1. Criação de conquistas adicionais não pertence a este publicador e requer escopo próprio.

## Recuperação e limites

- Cada escopo é atômico. Uma falha não deixa metade de learning ou metade de daily aplicada; daily pode falhar após learning ter sido concluído, pois são operações separadas.
- Preservar snapshot/planos privados e evidência de aplicação. Em estado incerto, reler e comparar antes de repetir; o executor não exclui documentos ou força overwrite.
- Após publicação de gabaritos, manter Rules restritivas durante qualquer recuperação. Não restaurar a regra ampla como rollback.
- Recuperação real exige nova autorização, bloqueio/contensão do backend e mutações condicionais revisadas. Nunca remover progresso, reduzir XP ou promover histórico a v3. Depois de uso real, preferir correção de catálogo coordenada, sem reversão cega dos metadados.
- Conclusões antigas continuam históricas e idempotentes, sem novo XP nem badges retroativos. Publicação não cria provas HMAC; dados existentes não foram inspecionados nesta etapa.
- Esta preparação não valida IAM, SDK/Functions publicados, segredo real, clientes antigos ou produção. O Android não foi alterado nem executado.
