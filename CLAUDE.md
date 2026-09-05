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

No `i2cset` desta imagem (toybox) o argumento `MODE` e obrigatorio, senao o erro
e o enganoso `mode too long`:

```bash
adb -s <id> shell i2cset -y -f 1 0x39 0x81 0xc0 b    # ATIME
adb -s <id> shell i2cget -y 1 0x39 0x94              # CDATA low
```

O `i2cdump -y 1 0x46` despeja os 192 bytes da matriz de LED. Ao ler o sensor de
luz manualmente, **descarte a primeira leitura**: o ciclo de integracao e de
178 ms e a primeira costuma vir do ciclo anterior. Repita duas ou tres vezes
antes de concluir qualquer coisa — uma medicao unica ja me levou a duvidar de
uma conclusao correta.

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

## Hardware: o que os numeros significam

**A placa e um Sense HAT rev. 2.** O `i2cdetect -y 1` no kit mostra `0x39`
(TCS3400, luz e cor), `0x46` (ATtiny88, joystick e matriz de LED), `0x5C`
(LPS25H), `0x5F` (HTS221), `0x6A` (LSM9DS1) e `0x1C` como `UU` — o magnetometro
esta preso a um driver do kernel. O material da disciplina documenta so os
enderecos da **V1** e nao menciona o `0x39`; nao estranhe a ausencia.

**A luminosidade nao e lux.** E a contagem bruta do canal *clear* do TCS3400,
com 178 ms de integracao e ganho 16x (`ATIME=0xC0`, `CONTROL=0x02`). A escala
vai de 0 a 65535 e muda se alguem mexer nesses registradores. Para plantas a
grandeza correta seria PAR em µmol·m⁻²·s⁻¹, que este sensor nao mede — o valor
serve para tendencia e para distinguir claro de escuro, nao como medida
fotometrica. Os canais R, G e B sao lidos do barramento e descartados;
`SensorReading` os carrega, `EnvironmentSample` nao.

**A matriz de LED contamina a leitura de luz.** Ela acende com um arco-iris ao
energizar a placa e esta a centimetros do sensor. Medido neste kit: 545 contagens
acesa contra 79 apagada, e numa segunda medicao 660 contra 171 — cerca de 490 a
545 contagens vinham da propria placa. Rode `sensehat_cli clear` na Pi antes de
qualquer coleta cujos dados importem, e desconfie de historico de luminosidade
anterior a essa descoberta. O app nao controla a matriz: fala so com `0x5F`,
`0x5C` e `0x39`.

**O arco-iris so volta cortando a energia** — reiniciar o Android nao o repoe,
porque quem o escreve e o firmware do ATtiny88 ao energizar, e o Android nunca
reescreve a matriz. Para restaurar sem tirar da tomada existe
`Codigo/firmware/matriz_arco_iris.sh` (e a mesma coisa em uma linha, no README).
As cores sairam do proprio framebuffer, lido com `i2cdump -y 1 0x46`.

**O framebuffer da matriz nao tem o layout que a documentacao sugere.** Nao e
`8R+8G+8B` por linha: sao tres planos de 64 bytes — vermelho `0-63`, verde
`64-127`, azul `128-191` — indexados por `y*8+x`, com 5 bits por canal. Descobri
sondando pixels isolados e vendo qual byte mudava; a suposicao errada custou uma
restauracao com 128 de 192 bytes divergentes. O `sensehat_cli setpixel` recebe
0-255 e aplica `valor >> 3`.

## Demonstracoes fora de casa

O ThingsBoard roda no MacBook, entao a Pi aponta para o IP **do Mac**, que muda
a cada rede. Descobrir com `ipconfig getifaddr en0` e atualizar nas
Configuracoes do app da Pi. Tres pontos que ja custaram tempo:

- **O emulador nao precisa disso**: configurado com host `10.0.2.2`, ele sempre
  alcanca o Mac, em qualquer rede.
- **Wi-Fi de escola e de evento costuma isolar clientes** entre si. Nesse caso a
  Pi nao alcanca o Mac com IP nenhum, e nao ha configuracao que resolva. A
  defesa e levar a propria rede (hotspot do celular).
- **O adb por rede quebra junto**, porque o IP da Pi tambem muda. Para mexer em
  codigo fora de casa, leve cabo USB.

## Armadilhas conhecidas

**Limiares calibrados contra ruido passam despercebidos.** Os limiares de luz
eram `>100` OK e `>=30` atencao. Como a matriz de LED injetava ~466 contagens em
toda leitura, o valor nunca descia de 100 — o alerta de pouca luz nao podia
disparar nem no escuro total, e ninguem notou porque o cartao vivia verde. Uma
funcionalidade morta que parece funcionar e mais dificil de achar que uma que
falha. Ao mexer em limiar, confirme que as duas pontas da faixa sao alcancaveis
na pratica.

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
