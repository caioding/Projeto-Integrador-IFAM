# GreenPi Monitor — Codigo do projeto

Sistema IoT de monitoramento ambiental para apoio ao estudo de plantas.
Uma **Raspberry Pi 5 com Android 16 (AOSP)** le os sensores do **Sense HAT**,
publica a telemetria por **MQTT** no **ThingsBoard** e exibe as condicoes do
ambiente num **aplicativo Android** com alertas por cor e historico.

Projeto Integrador — IFAM.
Autor: Caio Cesar.

---

## Estrutura das pastas

| Pasta | Conteudo |
|---|---|
| `app-android/` | Aplicativo Android em Kotlin (coletor e painel) |
| `firmware/` | Camada nativa de acesso ao I2C e script de validacao do hardware |
| `plataforma/` | ThingsBoard em Docker, provisionamento e dashboard |

Cada pasta tem o seu proprio `README.md` com detalhes.

---

## Arquitetura

```
┌──────────────── Raspberry Pi 5 — Android 16 (AOSP) ─────────────────┐
│                                                                     │
│  Sense HAT ──I2C──> libsensehat.so ──> SenseHat.kt ──┐               │
│  HTS221 · LPS25H · TCS3400            (JNI / NDK)    │               │
│                                                      v               │
│  /sys/class/thermal ──> temperatura do SoC ──> compensacao termica   │
│  WifiManager ──────────> RSSI                        │               │
│                                                      v               │
│                                        CollectorService (foreground) │
└──────────────────────────────────────────────────────┬──────────────┘
                                                       │ MQTT, QoS 1
                                                       │ v1/devices/me/telemetry
                                                       v
                        ┌─────────── ThingsBoard CE (Docker) ───────────┐
                        │  Broker MQTT · PostgreSQL · Dashboard web     │
                        └───────────────────────┬───────────────────────┘
                                                │ REST + JWT
                                                v
                            Aplicativo Android — modo painel
                        (na propria Pi 5, num celular ou no emulador)
```

---

## Ordem correta de execucao

### 1. Subir a plataforma

```bash
cd plataforma
docker compose up -d
docker compose logs -f          # aguarde "Started ThingsboardServerApplication"
```

A primeira execucao instala o banco de dados e leva alguns minutos.
Interface em `http://localhost:8080` — usuario `tenant@thingsboard.org`,
senha `tenant`.

### 2. Provisionar o dispositivo e o dashboard

```bash
cp config.example.env .env      # ajuste o IP e a senha
set -a && . ./.env && set +a
python3 provisionar_thingsboard.py
```

Anote o **token de acesso** impresso ao final.

### 3. Apagar a matriz de LED da Raspberry Pi

```bash
sensehat_cli clear
```

A matriz acende sozinha ao energizar a placa e contamina o sensor de luz em
centenas de contagens. E preciso repetir a cada corte de energia. Detalhes e
como restaurar o padrao original em `firmware/README.md`.

### 4. Compilar e instalar o aplicativo

```bash
cd ../app-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 5. Configurar o aplicativo

Na Raspberry Pi, abra o GreenPi Monitor, va em **Configuracoes** e preencha
host, portas, token e nome do dispositivo (ver `app-android/config.example.properties`).
Use **Testar conexao MQTT** para confirmar antes de iniciar.

### 6. Iniciar a coleta

Botao **Iniciar coleta** na tela principal. A partir dai a telemetria aparece:

- nos cartoes da propria tela, com alertas por cor;
- em **Historico**, dentro do app;
- no dashboard web do ThingsBoard;
- em qualquer celular com o mesmo APK, em modo painel.

---

## Quando a Raspberry Pi para de publicar

A plataforma guarda a ultima leitura e a devolve indefinidamente, entao um
sistema ingenuo seguiria exibindo aquele valor como se fosse a condicao atual.
Os dois lados sinalizam isso por conta propria:

- **No app**, passados 30 s sem amostra nova (tres periodos de coleta), o cartao
  vira `SEM CONTATO`, os valores ficam esmaecidos e a idade da ultima leitura
  aparece na tela.
- **No dashboard**, a faixa do topo le o atributo `active` do dispositivo, que o
  ThingsBoard derruba apos 60 s sem telemetria, e alterna entre `ONLINE` (verde)
  e `SEM CONTATO` (vermelho).

Os prazos diferem de proposito; o README principal explica o motivo.

---

## Configuracao e segredos

Nenhuma senha, token ou chave esta gravada no codigo-fonte. Os valores sao
informados em tempo de execucao e guardados em SharedPreferences. Os modelos de
configuracao ficam em:

- `app-android/config.example.properties` — parametros do aplicativo
- `plataforma/config.example.env` — variaveis do provisionamento

---

## Requisitos

| Componente | Versao usada |
|---|---|
| Raspberry Pi | 5 Model B Rev 1.1 (8 GB) |
| Sistema operacional | Android 16 (AOSP `aosp_rpi5`), SDK 36, build userdebug |
| Modulo de sensores | Raspberry Pi Sense HAT rev. 2 |
| JDK | 17 ou superior |
| Android SDK | 34 |
| NDK | 30.0.14904198 |
| CMake | 3.22.1 |
| Docker | 20.10 ou superior |
| Python | 3.9 ou superior (apenas para o provisionamento) |

---

## Telemetria publicada

Topico `v1/devices/me/telemetry`, QoS 1:

```json
{
  "temperature":     28.7,
  "temperature_raw": 41.1,
  "humidity":        42.0,
  "pressure":        1008.2,
  "light":           695,
  "cpu_temp":        57.9,
  "wifi_rssi":       -56,
  "status":          "ATENCAO"
}
```

## Faixas de referencia

| Grandeza | OK (verde) | Atencao (amarelo) | Alerta (vermelho) |
|---|---|---|---|
| Temperatura | 18 a 28 °C | 15–18 ou 28–32 °C | abaixo de 15 ou acima de 32 °C |
| Umidade | 40 a 70 %rH | 30–40 ou 70–80 %rH | abaixo de 30 ou acima de 80 %rH |
| Luminosidade | acima de 60 | 20 a 60 | abaixo de 20 |

O status geral publicado e sempre o **pior** entre as tres grandezas.

A luminosidade e dada em contagens brutas do TCS3400, nao em lux. As faixas
foram calibradas com a matriz de LED apagada — ver o passo 3 abaixo, sem o qual
a leitura fica inflada em centenas de contagens.

---

## Solucao de problemas

| Sintoma | Causa provavel e correcao |
|---|---|
| App mostra "Modo painel" na Raspberry Pi | O Sense HAT nao foi detectado. Rode `firmware/validar_sensores.sh` |
| "Testar conexao MQTT" falha | Host errado (nao use `localhost`), token invalido, ou a porta 1883 bloqueada por firewall |
| Historico vazio | A coleta ainda nao foi iniciada, ou o nome do dispositivo nao confere com o cadastrado |
| Temperatura muito acima da real | Auto-aquecimento do Sense HAT: calibre o fator `k` em Configuracoes (ver `app-android/README.md`) |
| Graficos do ThingsBoard vazios | Aguarde alguns segundos apos abrir o painel; a primeira carga do historico demora |
| Luminosidade alta demais e sempre "OK" | A matriz de LED esta acesa. Rode `sensehat_cli clear` na Pi |
| Sensor de luz devolve zero | Corte de energia devolveu o TCS3400 ao padrao. Reabrir o app o reconfigura |
| App diz "SEM CONTATO" com a Pi ligada | A coleta foi parada, ou a publicacao MQTT esta falhando. Confira o contador de envios na tela |
| Dashboard diz `ONLINE` com a Pi desligada | Provisionamento antigo. Rode `provisionar_thingsboard.py` de novo |
