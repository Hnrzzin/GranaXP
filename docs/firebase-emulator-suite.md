# Android com Firebase Emulator Suite

## V1 Spark vigente (E6.9C, 15/10/2026)

O aplicativo principal usa **Authentication + Firestore**. O emulador de
Functions na porta 5001 não é requisito; a pasta `functions/` documenta a
arquitetura anterior. Veja [o contrato Spark](spark-v1.md) para a matriz das
nove callables e os limites de XP, gabaritos e conquistas.

No Android Studio, abra um AVD dedicado em **Running Devices**, compile o APK
debug com `-PuseFirebaseEmulators=true`, instale o APK completo e execute os
testes instrumentados nele. O opt-in aponta apenas para `demo-granaxp`.
Execute a suíte local isolada de Auth e Firestore em `127.0.0.1:9099` e
`127.0.0.1:8080`, ou redirecione essas portas do dispositivo para uma suíte
dedicada com `adb reverse`. Na E6.9C, o AVD `GranaXP_E6_9C_Spark` usou
`emulator-5570`, com `tcp:9099 -> tcp:19099` e `tcp:8080 -> tcp:18080`.
O script `firebase-emulator-adb.ps1` gerencia somente **9099 e 8080**.
Confira `adb devices`, nome do AVD, projeto, reverse, hash do APK instalado
e JUnit real antes de atribuir o resultado à V1 Spark.
Não execute o aplicativo local sem esses endpoints: o opt-in não faz fallback
para `granaxp-d7927`.

O catálogo aprovado é publicado apenas no emulador de ensaio: 2 módulos,
3 lições históricas, 34 blocos, 9 atividades, 7 missões e controle. O
publicador `educational-publication-cli.mjs` permanece restrito a
`demo-granaxp`/`127.0.0.1:18080` e `127.0.0.1:28080` para aplicação local;
não há executor real. Os
gabaritos dos blocos e missões são **públicos para usuários autenticados**
depois da publicação no Firestore, mas não são embutidos no APK.

O gate `scripts/firebase-instrumented-gate.mjs` aceita `--mode local` para os
quatro testes de configuração e catálogo **vazio** numa suíte demo fresca;
`--mode spark` seleciona 22 testes instrumentados, incluindo lições, missão,
metas e UI, depois de publicar o catálogo no emulador; `--mode normal`
seleciona dois testes de configuração sem operação remota. Exemplo no AVD
dedicado, a partir da raiz do repositório e com Node 22:

```powershell
& $nodeFile scripts/firebase-instrumented-gate.mjs --serial emulator-5570 --mode spark --auth-port 19099 --firestore-port 18080
```

Substitua `$nodeFile` pelo caminho local do Node 22 descrito no HANDOFF. O
gate escreve `success.json` e o JUnit bruto em `app/build/reports/instrumented-gate/`.
Não rode o contrato de catálogo vazio sobre catálogo publicado.

Os testes da arquitetura anterior que exigem Functions/HMAC são históricos e
devem ser executados em suíte separada. Não use a suíte interativa E6.8 como
base descartável para testes que alteram catálogo ou contas. A seção abaixo
registra o procedimento **histórico E6.8 com Functions** e não descreve o Run
normal da V1 Spark.

## Histórico E6.8 — procedimento anterior com Functions

Este ambiente usa **somente `demo-granaxp`**, sem recursos reais. O opt-in
`-PuseFirebaseEmulators=true` afeta apenas debug. Release permanece normal.
Não altere `google-services.json`, targetSdk ou permissões para testar localmente.

## Executar o aplicativo principal com o catálogo aprovado

O mesmo código do produto oferece a grade de módulos, lições por etapas,
atividades, Missão Diária em modal, progresso e conquistas. A execução local do
principal mantém `applicationId=com.hnrzzin.granaxp`; o APK histórico separado
da apresentação usa `com.hnrzzin.granaxp.demo`. Não copie esse sufixo, contas,
progresso, configuração Firebase ou segredo fictício para a configuração normal.

Use um AVD dedicado ao principal e a suíte local protegida indicada no HANDOFF
operacional. Confira projeto `demo-granaxp`, endpoints abaixo, processo da suíte
e proteção dos arquivos de configuração antes de executar. Preserve a suíte
interativa e seus dados: não reinicie, importe ou rode testes que substituem
catálogos sobre uma sessão em uso. O runner `functions-emulator-runner.mjs`
permanece sob a restrição de isolamento I-I1 e não deve ser usado neste fluxo.

1. Compile com `-PuseFirebaseEmulators=true :app:assembleDebug`. O opt-in é
   por invocação; não o grave nas propriedades globais do Gradle.
2. Confira `USE_FIREBASE_EMULATORS=true` no BuildConfig gerado e, no Manifest
   do APK, `FirebaseInitProvider` desativado e cleartext habilitado. Registre o
   SHA-256 de `app/build/outputs/apk/debug/app-debug.apk`.
3. Confirme o nome do AVD com `adb -s <serial> emu avd name` e prepare os três
   encaminhamentos com `firebase-emulator-adb.ps1`. Instale o APK completo nesse
   dispositivo; não use overlay ou Apply Changes como prova da identidade.
4. Confira o hash do `base.apk` instalado e o teste
   `FirebaseEnvironmentTest#startupSelectsOneCoherentEnvironmentBeforeRepositoriesRun`,
   com confirmação de execução em modo `local`, antes do ensaio funcional.
5. Abra `com.hnrzzin.granaxp/.MainActivity` por ADB. Running Devices permite
   visualizar e controlar esse aplicativo dentro do Android Studio.

O catálogo precisa existir na suíte: 2 módulos, 3 lições com IDs históricos,
34 blocos incluindo 9 atividades, 7 missões e `learningCatalogControl/current`.
Ausência de dados não é corrigida por uma nova UI. Use somente o catálogo
aprovado e as contas locais permitidas para o ensaio; a publicação real tem
autorização e gates próprios.

**O botão Run padrão não conserva o opt-in da compilação anterior.** Sem a
propriedade explícita, debug volta à configuração Firebase normal. Compilar ou
abrir outro APK sobre o AVD de prova muda a versão observada. Ao finalizar o
ensaio, um build com `-PuseFirebaseEmulators=false` pode restabelecer os artefatos
normais sem substituir o aplicativo local que permanece instalado.

A equivalência deve ser comprovada por execução do principal, com APK/AVD,
catálogo e confirmação dos fluxos registrados. Esse ensaio não valida produção.

## Preparação reproduzível

Pré-requisitos: Android SDK/ADB, um Android Emulator ou aparelho autorizado por
USB, JDK compatível com o Gradle e Emulator Suite, Node 22 e npm. No Windows,
use `npm.cmd` se o PowerShell bloquear `npm.ps1`. Configure JAVA_HOME/PATH
no seu terminal; nenhum caminho específico desta máquina é obrigatório.

Na raiz do checkout, instale e compile as dependências locais:

```powershell
npm.cmd ci
npm.cmd --prefix functions ci
npm.cmd --prefix functions run build
```

Inicie a suíte em um terminal dedicado (mantenha-o aberto):

```powershell
npm.cmd exec -- firebase emulators:start --only auth,firestore,functions --project demo-granaxp
```

Em outro terminal, identifique o dispositivo e prepare os três encaminhamentos:

```powershell
adb devices
.\scripts\firebase-emulator-adb.ps1 -Serial emulator-5554
```

Substitua `emulator-5554` pelo serial listado. Se ADB não estiver no PATH:

```powershell
.\scripts\firebase-emulator-adb.ps1 -Serial emulator-5554 -AdbPath 'C:\seu-sdk\platform-tools\adb.exe'
```

O script verifica o dispositivo e os encaminhamentos. Pode ser repetido.
Refaça a preparação após reiniciar/desconectar o dispositivo ou servidor ADB.

| Serviço | Endpoint no Android | Destino no computador |
|---|---|---|
| Authentication | 127.0.0.1:9099 | 127.0.0.1:9099 |
| Firestore | 127.0.0.1:8080 | 127.0.0.1:8080 |
| Functions | 127.0.0.1:5001 | 127.0.0.1:5001 |

O Android usa loopback com `adb reverse`, sem `10.0.2.2` ou
`ACCESS_LOCAL_NETWORK`, inclusive com targetSdk 37. Sem suíte/encaminhamento,
as operações locais falham; não há troca para o Firebase real.

## Builds e testes

```powershell
# APK local; instale apenas este APK para testar a suíte
.\gradlew.bat -PuseFirebaseEmulators=true :app:assembleDebug

# Gate oficial local; exige suíte demo fresca e os três forwards preparados
node scripts/firebase-instrumented-gate.mjs --serial emulator-5554

# Configuração normal: apenas os dois testes sem operações de dados reais
node scripts/firebase-instrumented-gate.mjs --serial emulator-5554 --mode normal

# APK normal (não execute fluxos de dados reais sem autorização)
.\gradlew.bat :app:assembleDebug
```

O gate usa Node 22 já exigido pela suíte e o wrapper Gradle existente. Configure
JAVA_HOME/PATH como na preparação. Se ADB não estiver no PATH, informe
`--adb 'C:\seu-sdk\platform-tools\adb.exe'`. Serial é obrigatório; o gate não
prepara nem remove forwards. O último gate recompila o APK de seu próprio modo.

O Gradle é usado pelo gate para compilar os APKs; a instrumentação é executada
pelo ADB com o mesmo AndroidJUnitRunner. O modo local executa o pacote inteiro,
sem filtro de classe ou modo de listagem, e exige os quatro métodos obrigatórios.
Confere início/fim de cada teste, identidade, resultado, confirmação JUnit após
aprovação com token novo por execução, ambiente e conclusão normal do protocolo.
Zero testes, conjunto incompleto, falha, skip, abort/crash, timeout, evidência
antiga ou listagem sem execução retornam erro. O modo normal exige os dois
métodos existentes de configuração e não executa os testes que escrevem no demo.

**Sucesso isolado de `connectedDebugAndroidTest` não é um gate confiável neste
ambiente:** o motor do AGP pode emitir sucesso vazio após abort da instrumentação.
Não considere descoberta (`log=true`), exit code zero ou a frase “4 tests” como
prova. Use o gate acima e sua confirmação final de execução aprovada.

Evidências por execução ficam em
`app/build/reports/instrumented-gate/<modo>-<execução>/`: request/token/conjunto
esperado, stdout/stderr e resultado dos processos (incluindo Gradle), saída
bruta da instrumentação, logcat de processo/runner/crash e `success.json` ou
`failure.json`. Não limpe esses arquivos antes de investigar uma falha. O gate
não limpa buffers de logcat e separa crashes anteriores dos registros atuais.
Se ocorrer crash, examine saída bruta e logcat; não troque runner, dependência,
AGP, permissões ou targetSdk sem identificar a causa e obter autorização.

Regressão reproduzível do mesmo verificador usado pelo gate, sem dispositivo:

```powershell
node --test scripts/instrumentation-result.test.mjs
```

O teste de integração aborta antes de qualquer request se projeto/host
não forem demo/loopback. Ele cria e remove apenas fixtures locais; não é
um script de população de catálogo real. O teste normal verifica configuração
sem executar operações de dados e ignora os testes que escrevem no demo.

O catálogo global de lições exige resposta do servidor: catálogo vazio é um
sucesso válido, mas falta de rede, acesso negado e dados malformados produzem
erro na trilha. Um cache vazio não confirma ausência de conteúdo. A integração
instrumentada cobre esses casos, a recuperação e a chamada autenticada de
Functions. Não existe navegação offline garantida para esse catálogo.

As suites de backend têm seu próprio ciclo de emuladores. Pare a suíte
interativa antes de executá-las, evitando conflito nas portas:

```powershell
npm.cmd run test:functions
npm.cmd run test:firestore-rules
```

Para encerrar: pare a suíte com Ctrl+C e remova somente seus encaminhamentos:

```powershell
.\scripts\firebase-emulator-adb.ps1 -Serial emulator-5554 -Remove
```

A limpeza pode ser repetida: portas já ausentes são ignoradas. Se uma remoção
falhar, o script tenta as outras portas e retorna erro; ao final verifica que
nenhuma das três portas permanece encaminhada. Não remove encaminhamentos de
outras portas e não usa `reverse --remove-all`.

## Verificar a limpeza

Regressão automatizada, sem dispositivo ou Firebase, usando um comando ADB
controlado e o script real de limpeza:

```powershell
.\scripts\test-firebase-emulator-adb.ps1
```

Cobre três portas presentes, estado parcial, estado vazio, limpeza repetida,
preservação de outra porta, erro de remoção sem interromper as demais e
verificação final que rejeita um encaminhamento remanescente.

Para repetir os quatro cenários com ADB real, pare os testes/APK locais antes
de executar os comandos abaixo. Eles alteram somente os três encaminhamentos
reservados ao GranaXP; substitua serial e caminho do ADB conforme a máquina.

```powershell
$ErrorActionPreference = 'Stop'
$adbPath = 'adb'
$serial = 'emulator-5554'
foreach ($case in @(
    @{ Ports = @(9099, 8080, 5001); Runs = 1 },
    @{ Ports = @(8080, 5001); Runs = 1 },
    @{ Ports = @(); Runs = 1 },
    @{ Ports = @(9099, 8080, 5001); Runs = 2 }
)) {
    .\scripts\firebase-emulator-adb.ps1 -Serial $serial -AdbPath $adbPath -Remove
    foreach ($port in $case.Ports) {
        & $adbPath -s $serial reverse "tcp:$port" "tcp:$port"
        if ($LASTEXITCODE -ne 0) { throw "Falha ao preparar a porta $port." }
    }
    for ($run = 0; $run -lt $case.Runs; $run++) {
        .\scripts\firebase-emulator-adb.ps1 -Serial $serial -AdbPath $adbPath -Remove
    }
}
& $adbPath -s $serial reverse --list
```

Ao retomar o APK local, prepare novamente os três encaminhamentos.

Se ocorrer timeout, confira `adb reverse --list`, as três portas e o projeto
`demo-granaxp`. Não conceda permissões de rede local, reduza targetSdk ou
substitua o projeto demo para contornar o erro.
