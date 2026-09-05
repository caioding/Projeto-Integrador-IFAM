# GreenPi Monitor

Sistema IoT de monitoramento ambiental para apoio ao estudo de plantas.
Projeto Integrador — IFAM.

Uma **Raspberry Pi 5 com Android 16 (AOSP)** lê os sensores do **Sense HAT** pelo
barramento I2C, compensa o auto-aquecimento da placa, publica a telemetria por
**MQTT** no **ThingsBoard** e exibe as condições num aplicativo Android com
alertas por cor e histórico em gráficos.

**Autor:** Caio Cesar

---

## Como funciona

```
Sense HAT ──I2C──> camada nativa em C (JNI/NDK) ──> Kotlin ──> compensação térmica
                                                                      │
                                                          MQTT (QoS 1, 10 s)
                                                                      v
                                        ThingsBoard (broker + PostgreSQL + dashboard)
                                                                      │
                                                             REST + JWT
                                                                      v
                                    App Android — na Pi 5 ou num celular
```

O mesmo APK cumpre dois papéis, decididos em tempo de execução: com o Sense HAT
presente ele coleta e publica; sem ele, opera apenas como painel.

## Sensores utilizados

| Endereço | Chip | Grandezas |
|---|---|---|
| `0x5F` | ST HTS221 | Umidade relativa e temperatura |
| `0x5C` | ST LPS25H | Pressão barométrica e temperatura |
| `0x39` | AMS TCS3400 | Luminosidade e cor (RGBC) |

## Estrutura do repositório

| Caminho | Conteúdo |
|---|---|
| `Codigo/app-android` | Aplicativo Android em Kotlin + camada nativa em C |
| `Codigo/firmware` | Acesso de baixo nível ao I2C e script de validação do hardware |
| `Codigo/plataforma` | ThingsBoard em Docker, provisionamento e dashboard |
| `Outros/diagramas` | Diagramas de arquitetura, fluxo de dados e hardware (PNG e SVG) |
| `Outros/evidencias` | Capturas de tela do aplicativo e do dashboard |

## Começando

```bash
# 1. Plataforma
cd Codigo/plataforma
docker compose up -d
cp config.example.env .env      # ajuste o IP e a senha
set -a && . ./.env && set +a
python3 provisionar_thingsboard.py

# 2. Aplicativo
cd ../app-android
cp local.properties.example local.properties   # ajuste o caminho do SDK
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Depois, configure host, porta, token e nome do dispositivo na tela de
Configurações do app e acione **Iniciar coleta**.

Instruções completas em [`Codigo/README.md`](Codigo/README.md).

## Destaque técnico — compensação do auto-aquecimento

O Sense HAT fica a milímetros do SoC da Raspberry Pi 5, que opera entre 50 e
58 °C. O calor conduzido eleva a leitura do sensor em 10 a 15 °C. A correção
aplicada é:

```
T_ambiente = T_sensor − (T_CPU − T_sensor) / k
```

O fator `k` é ajustável na tela de configurações, porque depende da montagem.
O valor bruto continua sendo publicado em `temperature_raw`, o que mantém a
compensação auditável.

## Deteccao de perda de contato

Uma plataforma de telemetria guarda a ultima leitura recebida e a devolve
indefinidamente. Se a Raspberry Pi for desligada, tanto o app quanto o dashboard
continuariam exibindo aquele valor como se fosse a condicao atual — o modo de
falha mais perigoso do sistema, porque nao parece uma falha. Os dois lados
tratam isso de forma independente:

| Camada | Prazo | Como funciona |
|---|---|---|
| Aplicativo | **30 s** | Compara o timestamp da amostra, vindo da plataforma, com o relogio local. Vencido o prazo, o cartao vira `SEM CONTATO`, os valores ficam esmaecidos e a idade da ultima leitura aparece na tela. |
| Dashboard | **60 s** | O ThingsBoard mantem o atributo `active` do dispositivo e o derruba apos o `inactivityTimeout`. A faixa no topo do painel le esse atributo e alterna entre `ONLINE` (verde) e `SEM CONTATO` (vermelho). |

Os prazos sao diferentes de proposito. O app e o painel que se olha de perto,
entao reage rapido: `3 x intervalo de coleta`, com piso de 30 s, tolera duas
publicacoes perdidas por instabilidade de rede sem alarme falso. O ThingsBoard e
o registro historico, e um prazo maior evita marcar o dispositivo como inativo
por uma perda momentanea de pacote. Na pratica, o celular acusa primeiro e o
dashboard confirma em seguida.

Para alinhar os dois, ajuste o intervalo na tela de configuracoes do app e
reprovisione a plataforma com o mesmo valor:

```bash
TB_INACTIVITY_MS=30000 python3 provisionar_thingsboard.py
```

Nao vale reduzir abaixo de ~20 s: com coleta a cada 10 s, uma unica publicacao
perdida ja derrubaria o aviso.

## Requisitos

JDK 17+ · Android SDK 34 · NDK 30.0.14904198 · CMake 3.22.1 ·
Docker 20.10+ · Python 3.9+

Lista completa em [`Codigo/DEPENDENCIAS.md`](Codigo/DEPENDENCIAS.md).

## Segurança

Nenhuma credencial está gravada no código-fonte. Token do dispositivo e senhas
são informados em tempo de execução e guardados em SharedPreferences. Os modelos
de configuração estão em `config.example.properties` e `config.example.env`.
