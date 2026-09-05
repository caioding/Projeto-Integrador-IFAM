# Aplicativo Android — GreenPi Monitor

Aplicativo em Kotlin que le os sensores do Sense HAT na Raspberry Pi 5,
compensa o auto-aquecimento, publica a telemetria por MQTT no ThingsBoard e
exibe o ambiente com alertas por cor.

## Os dois papeis do mesmo APK

O aplicativo detecta em tempo de execucao se o barramento `/dev/i2c-1` existe:

| Onde roda | Modo | O que faz |
|---|---|---|
| Raspberry Pi 5 (com Sense HAT) | **Coletor + painel** | Le os sensores, compensa, publica por MQTT e mostra tudo na tela |
| Celular ou emulador | **Painel** | Consulta a plataforma pela API REST e exibe os valores atuais e o historico |

Cada aparelho guarda a **propria** configuracao em SharedPreferences: host,
token e senha nao sao compartilhados entre a Pi e o celular. Boa parte dos
problemas de conexao e configuracao divergente entre os dois. No emulador, use
host `10.0.2.2`, que sempre alcanca a maquina onde o ThingsBoard roda,
independente da rede.

## Estrutura do codigo

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt
│   └── sensehat_i2c.c        Acesso ao barramento I2C via ioctl(I2C_RDWR)
├── java/com/greenpi/monitor/
│   ├── SenseHat.kt           HTS221, LPS25H e TCS3400: registradores e conversoes
│   ├── SystemStats.kt        Temperatura do SoC (sysfs) e RSSI do Wi-Fi
│   ├── Environment.kt        Modelo da amostra, faixas de status e compensacao termica
│   ├── ThingsBoardMqtt.kt    Publicacao MQTT (Eclipse Paho)
│   ├── ThingsBoardRest.kt    Consulta REST autenticada por JWT
│   ├── CollectorService.kt   Servico de primeiro plano com o laco de coleta
│   ├── Repository.kt         Estado compartilhado entre servico e interface
│   ├── MainActivity.kt       Tela principal com os cartoes e o controle da coleta
│   ├── HistoryActivity.kt    Graficos do historico
│   ├── SettingsActivity.kt   Configuracao do servidor e dos parametros
│   └── LineChartView.kt      Grafico de linha desenhado no Canvas
└── res/                      Layouts, cores e tema
```

## Dependencias

Declaradas em `app/build.gradle`, baixadas pelo Gradle:

| Biblioteca | Versao | Para que serve |
|---|---|---|
| `org.eclipse.paho.client.mqttv3` | 1.2.5 | Cliente MQTT |
| `androidx.appcompat` | 1.7.0 | Activities compativeis |
| `com.google.android.material` | 1.12.0 | Componentes visuais |
| `androidx.constraintlayout` | 2.1.4 | Layouts |

Ferramentas necessarias: **JDK 17+**, **Android SDK 34**, **NDK 30.0.14904198**
e **CMake 3.22.1** (os dois ultimos sao usados para compilar a camada nativa).

## Como compilar

```bash
cd app-android
./gradlew assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Se o Android Studio nao estiver no caminho padrao, aponte o JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

E crie um `local.properties` com o caminho do SDK:

```
sdk.dir=/Users/<usuario>/Library/Android/sdk
```

## Como instalar

```bash
# Raspberry Pi 5 (via Wireless Debugging ou USB)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Com mais de um dispositivo conectado
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

## Ordem correta de execucao

1. **Suba a plataforma** — `docker compose up -d` na pasta `plataforma`.
2. **Provisione o dispositivo** — `python3 provisionar_thingsboard.py`; anote o
   token impresso ao final.
3. **Apague a matriz de LED** na Pi — `sensehat_cli clear`. Ela acende ao
   energizar a placa e contamina o sensor de luz em centenas de contagens.
4. **Instale o APK** na Raspberry Pi 5.
5. **Configure o app** — abra *Configuracoes* e preencha host, porta, token e
   nome do dispositivo (ver `config.example.properties`). Use *Testar conexao
   MQTT* para confirmar.
6. **Inicie a coleta** — botao *Iniciar coleta* na tela principal.
7. **Acompanhe** — no proprio app (*Historico*), no dashboard web do
   ThingsBoard, ou no celular com o mesmo APK em modo painel.

## Sinalizacao de leitura obsoleta

A plataforma devolve a ultima leitura gravada indefinidamente, entao o painel
precisa julgar a **idade** do dado em vez de apenas exibi-lo. O app compara o
timestamp que vem do ThingsBoard com o relogio local e, passados tres periodos
de coleta (no minimo 30 s), troca o cartao para `SEM CONTATO`, esmaece os
valores e mostra ha quanto tempo foi a ultima leitura.

Um ciclo de 5 s redesenha a tela mesmo sem dado novo; sem ele a interface
congelaria justamente quando a Raspberry Pi para de publicar.

## Calibracao da compensacao termica

O Sense HAT fica sobre o SoC da Raspberry Pi 5, que opera perto de 57 °C. Isso
eleva a leitura do HTS221 em torno de 12 a 15 °C. O app aplica:

```
T_real = T_sensor - (T_cpu - T_sensor) / k
```

O fator `k` depende da montagem (gabinete, ventilacao, posicao). Para calibrar:

1. Coloque um termometro de referencia ao lado da Raspberry Pi.
2. Compare com o valor exibido no cartao *Temperatura*.
3. Ajuste `k` em *Configuracoes*: **diminuir k aumenta a correcao**.

O valor bruto continua sendo publicado em `temperature_raw`, o que permite
auditar a compensacao e refazer a calibracao a qualquer momento.

## Permissoes usadas

| Permissao | Motivo |
|---|---|
| `INTERNET` | Publicacao MQTT e consultas REST |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Leitura do RSSI do enlace |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Coleta continua |
| `WAKE_LOCK` | Manter a coleta com a tela apagada |
| `POST_NOTIFICATIONS` | Notificacao do servico em primeiro plano |

O acesso a `/dev/i2c-1` nao exige permissao do Android: o dispositivo tem modo
`crw-rw-rw-` na imagem AOSP utilizada.
