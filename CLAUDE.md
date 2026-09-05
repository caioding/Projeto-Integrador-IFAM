# CLAUDE.md

Orientacoes para o Claude Code trabalhar neste repositorio.

## O que e o projeto

GreenPi Monitor — sistema IoT de monitoramento ambiental. Projeto Integrador do
IFAM. Uma Raspberry Pi 5 rodando Android 16 (AOSP) le os sensores do Sense HAT
por I2C, compensa o auto-aquecimento da placa, publica por MQTT no ThingsBoard e
exibe tudo num app Android.

O mesmo APK tem dois papeis, decididos em tempo de execucao pela presenca do
Sense HAT: na Pi ele coleta e publica; num celular ou emulador, opera so como
painel, lendo a plataforma por REST. **Cada aparelho guarda a propria
configuracao em SharedPreferences** — host, token e senha nao sao compartilhados
entre eles. Boa parte dos "bugs" relatados e configuracao divergente entre a Pi
e o celular.

## Estrutura

| Caminho | Conteudo |
|---|---|
| `Codigo/app-android` | App em Kotlin + camada nativa em C (JNI/NDK) |
| `Codigo/firmware` | Acesso de baixo nivel ao I2C e validacao do hardware |
| `Codigo/plataforma` | ThingsBoard em Docker, provisionamento e dashboard |
| `Outros/diagramas` | Diagramas de arquitetura, fluxo e hardware |
| `Outros/evidencias` | Capturas de tela |

## Ambiente desta maquina

Estas peculiaridades ja custaram tempo; confira antes de assumir que algo quebrou.

**Nao ha Java no PATH.** O `/usr/bin/java` do macOS e um stub que falha com
"Unable to locate a Java Runtime". O JDK real esta dentro do Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

**`local.properties` nao e versionado** (contem caminho de maquina). Se sumir, o
Gradle reclama de "SDK location not found". Recrie com:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > Codigo/app-android/local.properties
```

**A compilacao emite avisos alarmantes que sao inofensivos** — `WARNING:
sun.misc.Unsafe`, `restricted method in java.lang.System`, e as vezes um stack
trace do daemon do Kotlin seguido de "Using fallback strategy". Sao
incompatibilidades entre o Gradle 8.7 e o JDK 21 do Studio. O que importa e a
ultima linha dizer `BUILD SUCCESSFUL`.

**Ha dois aparelhos no adb** — a Raspberry Pi (por rede) e um emulador. Comandos
sem `-s` falham com "more than one device". Liste com `adb devices -l` e use o
id: o da Pi contem `_adb-tls-connect._tcp` e reporta `model:Pi_5`.

## Comandos

```bash
# Plataforma
cd Codigo/plataforma && docker compose up -d
python3 provisionar_thingsboard.py        # idempotente; imprime o token do dispositivo

# App
cd Codigo/app-android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
adb -s <id> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Diagnostico

O caminho mais rapido para saber se a telemetria esta viva nao passa pela
interface web (que exige login) — e o banco dentro do container:

```bash
docker exec thingsboard sh -c 'psql -U thingsboard -d thingsboard -t -A -F"|" -c \
  "select k.key, to_timestamp(l.ts/1000), coalesce(l.dbl_v::text,l.long_v::text,l.str_v) \
   from ts_kv_latest l join key_dictionary k on k.key_id=l.key \
   join device d on d.id=l.entity_id where d.name='"'"'GreenPi-RPi5'"'"' order by l.ts desc;"'
```

O usuario do Postgres e `thingsboard`, nao `postgres`. Para ver se ha sessao MQTT
aberta: `docker logs thingsboard --since 10m | grep openConnections`.

Tambem da para ler a tela da Pi sem sair do terminal, o que evita pedir
capturas ao usuario:

```bash
adb -s <id> exec-out screencap -p > tela.png
adb -s <id> shell uiautomator dump /sdcard/ui.xml   # texto e coordenadas dos elementos
```

## Convencoes

- **Codigo, comentarios e mensagens de commit em portugues**, sem acentos no
  codigo-fonte e nos scripts (o projeto e consistente nisso). O README e os
  arquivos markdown usam acentuacao normal.
- Comentarios explicam **por que**, nao o que. O codigo existente comenta as
  decisoes nao obvias — por que o dataKey precisa do bloco completo de settings,
  por que o timestamp vem da plataforma e nao do relogio local. Mantenha esse
  padrao em vez de narrar o que a linha ja diz.
- **Nenhuma credencial no codigo-fonte.** Token do dispositivo e senhas sao
  informados em tempo de execucao. Os modelos ficam em `config.example.env` e
  `config.example.properties`. Nao commite tokens obtidos do provisionamento.
- O `provisionar_thingsboard.py` e idempotente e reconstroi o dashboard inteiro a
  cada execucao. Mudancas no painel se fazem nele, nao pela interface web — o
  proximo provisionamento sobrescreveria.

## Armadilhas conhecidas

**Widgets do ThingsBoard falham em silencio.** Um dataKey sem o bloco completo de
`settings` simplesmente nao desenha a serie, sem erro nenhum. Por isso existe o
`serie_timeseries_template.json`, extraido de um widget criado pela interface.

**Atributos chegam ao widget como texto.** Em JavaScript, `"false"` e um valor
verdadeiro — um teste `value ? ... : ...` no atributo `active` retorna ONLINE
justamente com a Pi desligada. Normalize antes de comparar. Esse bug ja ocorreu
uma vez; ver o commit `85c285e`.

**A grade do dashboard nao detecta sobreposicao.** Ao mudar o `sizeY` de um
widget, desca os de baixo manualmente e verifique celula por celula.

**O ThingsBoard mantem a configuracao do dashboard em cache na aba aberta.** Depois
de reprovisionar, e preciso recarregar a pagina para ver a mudanca.
