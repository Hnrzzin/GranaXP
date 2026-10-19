# E6.9I — inventário administrativo somente leitura

Estado: preparação local autorizada; execução real depende de autorização posterior.
O executor E6.9H e `BLOCKED_E69H` permanecem intactos.

## Desenho e plano de execução

Pedido E6.9I é a especificação: adaptador separado para inventário de
`granaxp-d7927/(default)`, sem escrita e sem conexão real nesta etapa.
Implementação direta no checkout atual, preservando todo arquivo anterior.

1. Criar testes RED em `scripts/catalog-readonly.test.mjs`: alvo, identidade
   efetiva, permissões, transporte fechado, paginação, órfãos, consistência,
   erros, escopos independentes e comparação offline.
2. Implementar `scripts/catalog-readonly.mjs`, usando GoogleAuth já instalado
   e REST de leitura; reutilizar hashes/allowlists E6.6 e manifesto H.
   Nenhuma dependência nova, cliente Firestore com métodos de escrita ou ADC.
3. Executar os testes e regressões locais pertinentes, revisar o resultado,
   registrar hashes/evidências e Changelog 116 no HANDOFF externo. Parar.

Revisão deve focar: token diferente da identidade declarada; URL/redirect
que escape da allowlist; página omitida; JSON Firestore com tipos que não
podem ser convertidos sem perda; falso aceite editorial das conquistas.

Decisões: a autorização expressa de implementar/testar substitui pausas
genéricas de planejamento. Sem commits/worktrees ou arquivos de processo
adicionais; este documento reúne desenho, plano e runbook. Testes usam o
SDK de autenticação real com transporte OAuth e HTTP interceptados, além
de bloqueio de sockets. Nenhuma credencial real é carregada nos testes.

## Limites e verificações

`catalog-readonly.mjs` não importa `catalog-admin.mjs` e não expõe sessão,
cliente Firestore, write, batch, commit ou transação. Só admite comandos
`inventory` e `compare`. Toda solicitação de recurso passa por
`assertReadRequest`; verbos/caminhos/bodies/query parameters fora da lista
são negados antes do transporte. OAuth token/tokeninfo e
`projects:testIamPermissions` usam POST por definição da API, sem alterar
política nem dados. Não há método setIamPolicy. Redirects e retries estão
desabilitados; falha não vira snapshot vazio e nunca deixa inventário parcial.

Alvo fixo: projeto `granaxp-d7927`, número `136652496724`, banco `(default)`,
UID `81c02a82-fb39-40a3-b0b8-82439c8ba232`, Native/Standard,
`southamerica-east1`. Os valores são expectativas F/G, não confirmação atual.
Antes do catálogo, GET do projeto exige ID/número/ACTIVE; GET do banco exige
nome/UID/tipo/edição/localização. Não cria banco se 404 nem habilita API.

Identidade: arquivo explícito `authorized_user` existente, lido só depois
de validar alvo/escopo/confirmação/saída. Não há ADC, busca de credenciais,
conta Firebase CLI, metadata server, service-account key, impersonation ou
external_account. GoogleAuth 11.1.0 instalado em `functions/node_modules`
obtém access token; `getTokenInfo` exige email verificado igual ao
`--principal`, expiração superior a 60 segundos e scope `cloud-platform`.
O MESMO token verificado autentica os GETs e o teste IAM; não há refresh
durante o inventário. Token quase expirado interrompe, sem troca de identidade.
Credencial sem email verificável ou scope suficiente é bloqueio real, sem
fallback. Arquivo/refresh token nunca entra no relatório, stdout ou Git.

Permissões necessárias, verificadas com o mesmo token por
`projects/granaxp-d7927:testIamPermissions`:

- `resourcemanager.projects.get`: confirmar projeto.
- `datastore.databases.getMetadata`: confirmar banco; não confundir com
  `datastore.databases.get`, que permite iniciar/reverter transações.
- `datastore.entities.get` e `datastore.entities.list`: consultar catálogo.

É uma checagem conservadora no projeto; permissões condicionais concedidas
somente em outro recurso podem falhar nela. A leitura de cada coleção ainda
precisa ter sucesso. Não se exige papel Owner/Editor nem permissão de escrita;
também não se afirma que o principal existente não as possui. IAM pode
conceder leitura de outros dados no banco: a restrição a catálogo é do
adaptador, não uma proteção contra código alterado ou operador privilegiado.
Nenhuma configuração IAM é criada/alterada por este programa.

| Escopo escolhido | Consultas de dados permitidas |
|---|---|
| learning | modules, lessons, blocks somente das três lessons aprovadas, GET exato learningCatalogControl/current |
| daily | somente dailyMissions |
| achievements | somente achievements |

Os três IDs de lessons são `2iT6j1pA0vvq62oX7jO7`,
`gMmIv1XNtMhIrhBzDFKc` e `Eo6rq9c4HDAtZRR6TAjp`.
Não existe `all`, collection group, consulta livre, recursão ou leitura de
users, lessonProgress, achievementProgress, transações ou finanças. Metadados
de projeto/banco, OAuth e teste IAM são pré-condições explícitas de cada
inventário, não varredura de conteúdo pessoal.

ListDocuments usa paginação integral com pageSize=100 e showMissing=true.
Inclui referências de pais ausentes nas coleções permitidas, mas nunca
percorre subcoleções de IDs inesperados. `learningCatalogControl` não é
listado: somente o documento current é autorizado. Subcoleções arbitrárias
sob current ou outros caminhos não são investigadas. Isto não é inventário
recursivo completo do banco. Até 100 páginas por coleção/10.000 referências;
limite, token repetido, duplicata ou resposta de outro caminho interrompe.

Todas as consultas/páginas de dados usam um único readTime: Date do servidor
no GET do banco menos um segundo. Falta de Date ou diferença maior que cinco
minutos do relógio local interrompe. Não cria transação. ReadTime estabiliza
a fotografia, sem garantir que os dados continuarão iguais depois dela.
Uma futura escrita ainda requer novo inventário, janela Admin e precondições.

Snapshot contém campos completos, extras preservados e updateTime com
nanossegundos, sem perda silenciosa. Só valores JSON suportados são aceitos;
timestamp em campo, reference, bytes, geo, inteiro inseguro/NaN interrompem
para auditoria. Metadados updateTime são tratados separadamente. Pais
ausentes e IDs inesperados são registrados como bloqueadores de comparação,
sem serem removidos ou transformados. SHA-256 detecta alteração acidental;
não é assinatura nem atestado de origem remota.

## Comandos preparados — NÃO executados nesta etapa

Pré-condições de execução posterior: auditoria I aprovada, autorização
explícita de leitura nomeando os metadados/OAuth/teste IAM e cada escopo,
principal esperado definido pelo proprietário e credencial authorized_user
existente já aprovada com os scopes necessários. Este runbook não autoriza
login, emissão de credenciais, alteração de IAM/APIs ou leitura real.

Diretório externo protegido deve existir antes da execução. O arquivo de
credencial abaixo é o caminho designado, NÃO um arquivo criado ou localizado
nesta etapa. Se não existir ou a identidade divergir, parar para decisão,
sem copiar tokens do Firebase CLI ou tentar ADC. A saída usa criação
exclusiva/mode0600; em Windows conferir ACL do diretório. Nomes já existentes
devem ser preservados; usar nova autorização/nome para repetir.

Comandos PowerShell exatos, para revisão/autorização posterior:

```powershell
$nodeFile = 'C:\Users\close\AppData\Local\Temp\granaxp-node22-runtime\node_modules\node\bin\node.exe'
$adapterFile = 'C:\Users\close\StudioProjects\GranaXP\scripts\catalog-readonly.mjs'
$inventoryDir = 'C:\IA\contexto\GranaXP_E6_9I_2026-10-16'
$expectedPrincipal = Read-Host 'Principal exato aprovado para esta leitura'
$readerArgs = @('--project', 'granaxp-d7927', '--database', '(default)',
  '--project-number', '136652496724', '--database-uid', '81c02a82-fb39-40a3-b0b8-82439c8ba232',
  '--principal', $expectedPrincipal, '--credential-file', "$inventoryDir\reader-credentials.private.json")

# Cada linha exige autorização própria do escopo; nenhuma foi executada na I.
& $nodeFile $adapterFile inventory @readerArgs --scope learning --confirm-read "READ_ONLY/granaxp-d7927/(default)/learning/$expectedPrincipal" --out "$inventoryDir\learning-inventory.private.json"
& $nodeFile $adapterFile inventory @readerArgs --scope daily --confirm-read "READ_ONLY/granaxp-d7927/(default)/daily/$expectedPrincipal" --out "$inventoryDir\daily-inventory.private.json"
& $nodeFile $adapterFile inventory @readerArgs --scope achievements --confirm-read "READ_ONLY/granaxp-d7927/(default)/achievements/$expectedPrincipal" --out "$inventoryDir\achievements-inventory.private.json"
```

Não executar as linhas seguintes automaticamente quando uma falhar; consultar
exit code e revisar cada resultado antes de prosseguir. Em ambiente com
variáveis de proxy/emulador/credenciais alternativas, a ferramenta recusa;
preparar processo limpo, sem mudar a configuração global da máquina.

Após inventários autorizados completos, comparar OFFLINE cada arquivo:

```powershell
$bundleFile = 'C:\IA\contexto\GranaXP_E6_6_2026-10-14\catalog-bundle.private.json'
& $nodeFile $adapterFile compare --inventory "$inventoryDir\learning-inventory.private.json" --bundle $bundleFile --out "$inventoryDir\learning-comparison.private.json"
& $nodeFile $adapterFile compare --inventory "$inventoryDir\daily-inventory.private.json" --bundle $bundleFile --out "$inventoryDir\daily-comparison.private.json"
& $nodeFile $adapterFile compare --inventory "$inventoryDir\achievements-inventory.private.json" --bundle $bundleFile --out "$inventoryDir\achievements-comparison.private.json"
```

## Comparação e próximo gate

O bundle é validado integralmente pelo E6.6, hash canônico
`01353e49cfa38b61c791eaad1e75ad8c6f34ae949f3e9aae7bb1fff9c4fe99ae`.
Comparação registra caminhos, hashes e status absent/exact/legacy/conflict,
sem reproduzir campos no relatório. Campos extras viram conflito quando
diferem da baseline aprovada; não são descartados. Learning distingue
legado exato de baseline+payload novo e exige as três lessons históricas;
controle esperado no bootstrap protocolo1/geração2. Geração diferente exige
análise do planejador, sem forçar redução. Daily compara sete definições.
Achievements compara cinco candidatas e preserva as três históricas por
hash, com verificação de critério/tipo, sem inventar aprovação de seus textos.

Manifesto H permanece `PENDING_OWNER_REVIEW`, productionApproved=false,
digest `8bb55c5ebc0824729096a6dea059d81b0235bd51746670adff3ecbe716efaca8`.
Nome approved não converte fixture em conteúdo aprovado. Histórico ausente,
órfãos, extras/IDs inesperados ou conteúdo diferente bloqueiam preparação
automática de publicação. A comparação é diagnóstico, nunca autorização:
`publicationAuthorized=false` e `dependenciesVerified=false` sempre.
Daily/achievements não leem learning silenciosamente; dependências exigirão
inventário learning próprio e revalidação numa futura etapa de escrita.

Não alimentar automaticamente o apply H com estes arquivos: o envelope I tem
metadados/digest e não é o snapshot interno H. Qualquer futura composição
de documents/dependencies e adaptador real de escrita exige etapa separada;
H continua bloqueado. Aprovações learning40, daily7 e achievements5 continuam
independentes. A escrita real ainda não possui executor autorizado.

Parar em 401/403 e em 404 de projeto, banco ou coleção. A única exceção é
o GET exato `learningCatalogControl/current`: 404 registra controle ausente
para análise do bootstrap, sem criar nada. Parar em identidade/alvo divergente, API/permissão ausente,
formato não suportado, inventário incompleto ou alteração concorrente; não
conceder IAM, habilitar APIs, repetir escrita, publicar Rules ou mudar billing.
As Rules F/G continuam fotografia datada, sem releitura nesta etapa. O leitor
administrativo não prova isolamento de usuários Android. Smoke real,
aprovação editorial de cinco conquistas/oito cards e distribuição permanecem
gates separados.

## Fontes verificadas para o contrato de leitura

- [ListDocuments: showMissing, readTime e paginação](https://docs.cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.documents/list).
- [GetDatabase](https://docs.cloud.google.com/firestore/docs/reference/rest/v1/projects.databases/get).
- [Permissões Firestore por método](https://docs.cloud.google.com/firestore/native/docs/security/iam).
- [testIamPermissions: consulta de permissões, sem setIamPolicy](https://docs.cloud.google.com/resource-manager/reference/rest/v1/projects/testIamPermissions).
- [Google Auth Library: getTokenInfo](https://github.com/googleapis/google-auth-library-nodejs).

## Evidência local E6.9I — 16/10/2026

```powershell
$env:GRANAXP_APPROVED_BUNDLE = 'C:\IA\contexto\GranaXP_E6_6_2026-10-14\catalog-bundle.private.json'
& $nodeFile --test --test-concurrency=1 scripts/catalog-readonly.test.mjs scripts/educational-publication.test.mjs scripts/catalog-admin.test.mjs scripts/achievement-publication.test.mjs
```

Rodada final: **42/42**, sem falhas, skips ou cancelamentos: 18 novos,
8 E6.6 offline, 12 executor H offline, 4 manifesto/planner de conquistas.
Node22.23.3; GoogleAuth11.1.0 real com HTTP OAuth interceptado, fetch de
recursos interceptado, sockets bloqueados nos testes novos e processos
filhos das CLIs novas. Nenhuma credencial real carregada e nenhum emulador,
AVD/ADB ou Firebase real acionado. Registros completos no TEMP indicado
no Changelog116 do HANDOFF externo. Sintaxe Node e diff checks passaram.
Não houve nova execução dos81 testes H completos, Rules, Functions ou Android.

Revisão estática independente: zero Critical/Important, um Minor documental
sobre 404 do controle, corrigido acima. Negativas adicionais de relógio,
expiração, paginação, URLs e 404 passaram. A suíte prova o caminho local
com transporte interceptado; identidade/IAM/API/banco remotos continuam
não verificados e dependem de autorização posterior. Auditoria gerencial I
permanece pendente.
