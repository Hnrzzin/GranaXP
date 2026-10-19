# GranaXP V1 Spark — contrato local E6.9C

**Estado em 15/10/2026:** implementação e Rules locais. Nenhuma Rules, catálogo ou APK desta etapa foi publicado em `granaxp-d7927`. A arquitetura de produção proposta para a V1 é Android + Firebase Authentication + Cloud Firestore + Firestore Security Rules. Cloud Functions estão fora da V1 Spark; `functions/` permanece como histórico de E6.9A e possível evolução futura.

## Nove callables anteriores

| Callable | Uso na V1 e chamador antigo | Classe | Destino Spark |
|---|---|---|---|
| `openLesson` | Abrir lição; `LessonRepository` | A | Leitura do catálogo e criação idempotente de `lessonProgress` pelo dono. |
| `submitLessonActivity` | Responder atividade; `LessonRepository` | B | Resposta conferida no Android com gabarito público; Rules aceitam apenas acréscimo de ID de bloco ACTIVITY da lição. Não há prova de acerto confiável. |
| `completeLesson` | Concluir lição; `LessonRepository` | B | Rules exigem três atividades registradas; conclusão única. `xpReward` vira somente XP educacional de exibição. |
| `createGoal` | Criar meta; `GoalRepository` | A | Escrita direta do dono com esquema validado; recompensa sempre zero. |
| `updateGoalProgress` | Atualizar meta; `GoalRepository` | B | Escrita direta do dono. Valores financeiros são autodeclarados e não autorizam 200 XP. |
| `updateGoalDetails` | Editar meta; `GoalRepository` | A | Escrita direta do dono, sem prêmio. |
| `deleteAccount` | Exclusão de conta; `AuthRepository`/Perfil | D | Desativada na V1 local. O SDK permite apagar o próprio usuário Auth após autenticação recente, mas isso não apaga automaticamente suas subcoleções Firestore. Não executar exclusão parcial. |
| `openDailyMission` | Carregar missão/modal; `DailyMissionRepository` | B | Consulta direta do catálogo/progresso; streak derivado dos dias concluídos. Data e seleção vêm do dispositivo, sem autoridade de servidor. |
| `submitDailyMissionAnswer` | Responder missão; `DailyMissionRepository` | B | Gabarito público validado localmente; criação única do progresso diário, sem XP nem concessão canônica de streak/conquista. |

Classes: A = operação cliente com validação suficiente para o efeito preservado; B = experiência preservada com garantia conscientemente reduzida; D = efeito sensível indisponível sem backend de confiança. Nenhuma dessas callables é usada no fluxo normal do APK.

## Catálogo, aprendizado e gamificação

O publicador E6.6 preserva os hashes aprovados, IDs e conteúdo: 2 módulos, 3 lições históricas, 34 blocos (9 atividades), 7 missões, durações 8/9/10 minutos e `xpReward` visual 50/100/150. O bundle privado e o manifesto de aprovação continuam fora/do repositório respectivamente; o APK não contém bundle, gabarito literal nem chave HMAC. **Após uma futura publicação, `correctAnswerIds` estará nos documentos Firestore legíveis por qualquer conta autenticada. Não é segredo.** Não usar essa resposta nem o progresso que o usuário pode registrar como prova de aprendizado, certificação ou elegibilidade financeira.

`users/{uid}/lessonProgress/{lessonId}` é criado vazio pelo próprio usuário. Cada atualização acrescenta uma atividade existente da lição; a conclusão exige três IDs, é monotônica e não pode ser repetida para alterar `completedAt`. As Rules não conseguem provar que o usuário escolheu a resposta correta. Lições históricas continuam legíveis; campos legados não são apagados ou promovidos a prova nova.

XP e nível persistidos em `users/{uid}` ficam protegidos contra qualquer alteração cliente e começam em 0/1. O aplicativo calcula XP/nível **somente para exibição** a partir das lições marcadas como concluídas e do `xpReward` do catálogo. Uma pessoa com cliente modificado pode fabricar seu próprio progresso permitido e, portanto, sua visualização educacional. Nenhuma função financeira, privilégio ou dado de terceiro depende desse XP. O dashboard/perfil deve sinalizar essa natureza educacional. Missão Diária, metas e conquistas não concedem XP persistido.

Metas continuam criáveis, editáveis e concluíveis, com XP 0. `rewardPolicyVersion` e `createdAt` históricos de E6.9A não podem ser criados/alterados pelo cliente. Não há emissão de autorização de 200 XP na arquitetura Spark.

O histórico de `achievementProgress` global e por usuário permanece somente leitura para o cliente. Conquistas novas são derivadas na interface dos próprios dados; são gamificação local, sem write de `isUnlocked`, prêmio ou certificação. Registros canônicos/históricos anteriores são exibidos sem migração ou exclusão. `MONTHLY_SAVINGS` não ganhou uma regra inventada.

Missão Diária escolhe a definição do dia no catálogo de sete itens usando a data `America/Sao_Paulo` do dispositivo. Acerto/erro aparece no modal; somente o acerto grava um documento diário com `completedAt` do servidor. Reabrir e reenviar não duplicam o documento nem dão XP. As Rules garantem propriedade, formato, existência da missão e criação única; **não conseguem validar a data real do dispositivo nem a resposta**, logo streak é apenas visual, derivado do progresso que o usuário pode autodeclarar.

## Segurança e operação

- Rules negam escrita cliente em `modules`, `lessons`, `blocks`, `dailyMissions`, `learningCatalogControl`, `achievements`, XP persistido, streak canônico, progresso canônico de conquistas, recibos e autorizações de metas. Dados privados são lidos/escritos só pelo UID do caminho quando a coleção permite escrita.
- Firestore Rules não protegem contra Admin SDK/IAM indevido; publicação e alteração do catálogo exigem operador confiável e autorização independente. O publicador atual só aplica em `demo-granaxp` nos loopbacks dedicados `127.0.0.1:18080` (ensaio Android) e `127.0.0.1:28080` (testes de integração); não possui executor de produção.
- Exclusão de conta completa está desativada no Perfil. Não orientar o usuário a apagar apenas Firebase Auth: isso deixaria dados Firestore órfãos e acessíveis ao administrador. Antes de publicar, decidir e implementar um processo de exclusão/retensão coerente com a política aplicável.
- Release não deve selecionar emulador. O opt-in `-PuseFirebaseEmulators=true` é exclusivo de debug; emulador local requer somente Auth e Firestore, com ADB reverse 9099/8080. Nenhum emulador local prova estado do Firebase real.
- A publicação futura exige revisão deste contrato, snapshot atual do projeto real, publicação coordenada de Rules e catálogo, verificação do APK normal e autorização explícita. E6.9C não concede essa autorização.
