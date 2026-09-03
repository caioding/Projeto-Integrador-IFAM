# GreenPi Monitor

Sistema IoT de monitoramento ambiental para apoio ao estudo de plantas.
Projeto final do **Curso de Android e Internet das Coisas (IoT)** —
Instituto de Pesquisas Eldorado.

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
| `Entrega/Codigo/app-android` | Aplicativo Android em Kotlin + camada nativa em C |
| `Entrega/Codigo/firmware` | Acesso de baixo nível ao I2C e script de validação do hardware |
| `Entrega/Codigo/plataforma` | ThingsBoard em Docker, provisionamento e dashboard |
| `Entrega/Codigo/midia` | Imagens da demonstração |
| `Entrega/Outros` | Diagramas, evidências, roteiro da apresentação e anotações |
| `Entrega/*.pptx` | Status Report 1 e Status Report 2 |
| `Entrega/*.docx` | Relatório de Projeto Final |
| `PLANEJAMENTO.md` | Planejamento e levantamento técnico inicial |

## Começando

```bash
# 1. Plataforma
cd Entrega/Codigo/plataforma
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

Instruções completas em [`Entrega/Codigo/README.md`](Entrega/Codigo/README.md).

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

## Requisitos

JDK 17+ · Android SDK 34 · NDK 30.0.14904198 · CMake 3.22.1 ·
Docker 20.10+ · Python 3.9+

Lista completa em [`Entrega/Codigo/DEPENDENCIAS.md`](Entrega/Codigo/DEPENDENCIAS.md).

## Segurança

Nenhuma credencial está gravada no código-fonte. Token do dispositivo e senhas
são informados em tempo de execução e guardados em SharedPreferences. Os modelos
de configuração estão em `config.example.properties` e `config.example.env`.
