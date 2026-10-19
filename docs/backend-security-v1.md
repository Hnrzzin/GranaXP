# Histórico E6.9A — backend com Functions

> **Substituído para a V1 Spark em 15/10/2026.** A arquitetura operacional
> vigente e os limites de segurança estão em [spark-v1.md](spark-v1.md).
> Nenhuma das Functions descritas abaixo foi implantada em `granaxp-d7927`.
> A matriz de ativação, HMAC, gatilhos e Blaze abaixo pertence ao plano
> histórico E6.9A e não deve ser seguida para publicar a V1 Spark.

Este contrato foi implementado localmente. Não é registro de deploy, migração, segredo real ou aprovação de produção. A E6.9 deve repetir o pré-flight sobre os hashes auditados. Billing/Blaze permanece requisito operacional a confirmar com o proprietário; não foi alterado.

## Nove callables e disponibilidade

`functions/src/backendAccess.ts` concentra o mapa fechado `BACKEND_CALLABLE_MODES`, um parâmetro JSON server-side. Somente os nove nomes abaixo são aceitos; campos ausentes significam `disabled`. JSON inválido, tipo inválido, nome desconhecido ou modo desconhecido falham fechados. O antigo `BACKEND_ACCESS_MODE` não autoriza nenhuma callable, inclusive quando seu valor é `open`.

Todas as nove exigem Firebase Auth antes de olhar configuração, dados, histórico ou idempotência. `open` admite usuário autenticado, preservando as validações do negócio e o UID do contexto. `pilot` exige correspondência exata ao parâmetro server-side `BACKEND_PILOT_UID`; UID ausente, inválido ou com espaços é recusado. O payload do Android não escolhe modo ou identidade.

**Defaults implantáveis: todas disabled.** Matriz abaixo é proposta para uma futura ativação, sem aplicação real nesta etapa:

| Callable | Finalidade | Auth | Modo proposto | Justificativa |
|---|---|---|---|---|
| openLesson | Abertura educacional, projeção sem respostas | Sim | pilot | Exige catálogo, HMAC e gate de histórico antes da ampliação. |
| submitLessonActivity | Validação protegida de atividade | Sim | pilot | Autoridade/provas HMAC permanecem no servidor. |
| completeLesson | Conclusão, XP e módulo | Sim | pilot | Exige idempotência, provas atuais e conteúdo publicado. |
| openDailyMission | Missão e estado do dia | Sim | pilot | Catálogo diário ainda depende de autorização separada. |
| submitDailyMissionAnswer | Resposta, streak, marcos e XP | Sim | pilot | Operação educacional sensível, validação e recompensa no backend. |
| createGoal | Gerenciamento normal de metas | Sim | open | Não premia saldo autodeclarado. Mantém requestId/fingerprint. |
| updateGoalProgress | Gerenciamento sensível de metas | Sim | open | 200XP exige autorização privada e recibo único. |
| updateGoalDetails | Gerenciamento normal de metas | Sim | open | Edição não concede XP nem torna saldo declarado uma prova. |
| deleteAccount | Exclusão sensível da própria conta | Sim | open | UID do contexto; sem possibilidade de indicar outra conta. |

Nenhuma dessas nove é uma API administrativa. O mapa proposto não é default, não é segredo HMAC e não deve ser copiado para produção sem conferir todas as dependências e a janela de implantação. As callables continuam em `southamerica-east1`.

## Conquistas: escrita somente no backend

- `achievements` continua catálogo sem escrita cliente.
- `achievementProgress` da raiz é histórico somente leitura, filtrado por UID. Criação, atualização e exclusão cliente são negadas para todos os tipos.
- `users/{uid}/achievementProgress/{achievementId}` continua canônico e somente gravável pelo backend.
- O Android consulta e combina histórico e canônico; não decide nem escreve novas concessões. Um histórico desbloqueado de tipo legado permanece visível quando o canônico está bloqueado. Isso não transforma o histórico em prova de condição protegida nem certifica a origem de registros antigos.
- `MODULE_COMPLETION` e `STREAK` permanecem nos fluxos autoritativos existentes. Módulo agora revalida também as provas HMAC atuais das outras lições, além da conclusão v3; flags legadas isoladas não bastam. Não há XP concedido pelo sincronizador de conquistas.

Três gatilhos Firestore internos preservam os eventos legítimos sem criar uma décima callable:

| Function | Path | Efeito |
|---|---|---|
| syncTransactionAchievements | users/{uid}/transactions/{documentId} | TRANSACTION_COUNT, categoria FINANCAS, a partir da contagem atual persistida. |
| syncGoalAchievements | users/{uid}/goals/{documentId} | GOAL_COUNT, a partir da contagem atual persistida. |
| syncLessonAchievements | users/{uid}/lessonProgress/{documentId} | LESSON_COUNT, categoria EDUCACAO; conclusão v3 só conta com todas as provas HMAC válidas contra o catálogo atual. |

Esses eventos são internos, não endpoints disponíveis ao Android. Têm controle separado `ACHIEVEMENT_SYNC_MODE` (`disabled` por default, `pilot` ou `open`) e usam o mesmo UID de piloto quando aplicável. A proposta futura é liberar sincronização após Rules/legado auditados; isso não abre as callables educacionais. O gatilho de lições também requer vínculo ao secret HMAC.

Os gatilhos consultam o estado atual em transação, ignorando a ordem do evento como autoridade. Repetição não regrava um estado idêntico ou troca `unlockedAt`; exclusões posteriores não revogam desbloqueio canônico já concedido. Evento atrasado não recria perfil excluído. Entrega pode ser repetida ou fora de ordem, conforme [contrato Firestore](https://firebase.google.com/docs/functions/firestore-events); concessões são eventualmente consistentes. Perfil deve ser consultado novamente após a sincronização para observar a novidade.

Contar transações registradas significa uso do produto, não certificação bancária. Isso não serve como elegibilidade dos200XP. `MONTHLY_SAVINGS` permanece apenas com leitura histórica: não havia fluxo de concessão implementado e não foi inventada heurística de poupança. Criar uma regra nova desse tipo exige especificação própria.

## Recompensa de metas: raiz de confiança explícita

Antes: criação já concluída ou cruzamento de alvo com valores declarados podia conceder200XP; requestId só protegia a mesma solicitação. Depois: criar/editar/progredir metas continua possível, mas declaração financeira e requestId novo nunca geram autorização.

Novas metas recebem `rewardPolicyVersion=1` e `createdAt` do servidor. Metas antigas permanecem como estão. Criação de meta já concluída retorna XP0 e mantém conclusão registrada; edição não concede XP.

A autorização opcional é privada em `users/{uid}/goalRewardAuthorizations/{goalId}`:

| Campo | Contrato |
|---|---|
| version | Inteiro1. |
| uid / goalId | Correspondência exata ao usuário autenticado e à meta. |
| conditionId | ID estável da condição real verificada;8–128 caracteres alfanuméricos, `_` ou `-`. Não um requestId novo por tentativa. |
| goalCreatedAt | Timestamp igual ao createdAt da meta autorizada; impede reaproveitar autorização numa recriação. |
| targetAmount | Igual ao alvo atual da meta. Alteração do alvo invalida autorização anterior. |
| verifiedAmount | Número finito, verificado por autoridade confiável, suficiente para o alvo. Não vem do cliente. |
| verifiedAt | Timestamp entre a criação da meta e o momento do consumo. |
| expiresAt | Timestamp ainda não expirado no consumo. |

**Não existe emissor automático de autorização neste pacote.** Somente um processo administrativo confiável, com verificação independente e autorização operacional própria, poderá emitir esses documentos. Um Admin SDK autenticado não prova sozinho a veracidade econômica: o operador/emissor precisa comprovar e manter a unicidade da condição. Gerar conditionId novo para o mesmo fato quebraria o protocolo do emissor. Registros financeiros que o próprio usuário pode criar não são usados como substituto dessa verificação. Não foi criada integração bancária, chave de cliente ou callable de emissão.

Sem esse emissor/autorização, as metas funcionam com XP0. É uma limitação funcional deliberada e explícita até haver fonte confiável; não apresentar200XP automáticos como ativados em produção. Nos testes, o Admin do emulador representa a autoridade confiável e emite somente fixtures fictícias.

`updateGoalProgress` só premia um cruzamento novo de meta do contrato1 ainda não concluída, após validar a autorização. Cria `users/{uid}/goalRewardClaims/{conditionId}` com goalId,200XP e timestamp, na mesma transação de meta+XP/nível. A existência do recibo impede outra recompensa da mesma condição para o usuário, mesmo em outra meta/requestId, concorrência ou perda de resposta. Recibo não é removido na exclusão individual da meta. Dados históricos não recebem autorização, marcador ou XP retroativo.

Todas as leituras precedem as escritas; o [contrato transacional Firestore](https://firebase.google.com/docs/firestore/manage-data/transactions) preserva atomicidade. Uma falha anterior ao commit não deixa recibo, meta ou XP parcialmente gravados. A soma do saldo da meta continua seguindo o contrato V1 existente: repetir `amountToAdd` pode somar novamente; a garantia nova é de idempotência da recompensa, não um novo protocolo de idempotência do lançamento financeiro.

Rules negam leitura/escrita cliente às autorizações/recibos pelo default deny, e impedem o cliente de fabricar `rewardPolicyVersion`/`createdAt` editando a meta.

## Validação e retomada

Executar Rules, Functions completas, guard individual, negativas de autorização, recibo único/retry/concorrência/rollback, conquistas e compatibilidade Android em ambiente dedicado. Não usar o runner principal bloqueado por TOCTOU; não utilizar a Demo interativa como base descartável de testes.

Antes de produção: auditoria do diff específico, snapshot atual, contenção por Rules, inventário pós-contenção de marcadores/histórico e escritores, emissor/condições de recompensa e política de disponibilidade, IAM/APIs/Blaze e secret real. Há nove callables mais três gatilhos novos a revisar no manifesto de deploy; os gatilhos exigem avaliação das dependências Eventarc e permissões mínimas, sem autorização implícita para habilitá-las. Deploy parcial sem os gatilhos não preserva novos desbloqueios das contagens legadas.

Não restaurar Rules permissivas nem escritor Android como recuperação. Conter a operação por configuração, preservar dados e investigar. Catálogo/controle/migração continuam em gates separados; o bloqueio CLI E6.7 não é corrigido por este pacote.
