# GreenPi Monitor — Planejamento do Projeto Final

**Autor:** Caio Cesar · **Curso:** Android e Internet das Coisas (IoT) — Instituto de Pesquisas Eldorado
**Data do planejamento:** 03/09/2026

> Escopo ajustado conforme decisão do autor: **sem módulo de Machine Learning** (removido do pitch original).

---

## 1. Levantamento técnico (CONCLUÍDO — validado em hardware real)

Todo o planejamento abaixo está apoiado em medições feitas diretamente no kit, não em suposições.

| Item | Valor verificado |
|---|---|
| Placa | Raspberry Pi 5 Model B Rev 1.1 (8 GB) |
| SO | Android 16 (AOSP `aosp_rpi5`), SDK 36, arm64-v8a, build `userdebug` |
| SELinux | **Permissive** (`ro.debuggable=1`) |
| Rede | Wi-Fi `wlan0` 192.168.0.18/24, 5745 MHz (802.11ac), RSSI −57 dBm |
| Display | Raspberry Pi Touch Display 2 (touch Goodix gt911) |
| HAL de sensores Android | **Ausente** (`dumpsys sensorservice` → "No Sensors on the device") |
| Barramento | `/dev/i2c-1`, permissão `crw-rw-rw-` (acessível sem root) |

### Sensores detectados no barramento I2C (`i2cdetect -y 1`)

| Endereço | Chip | WHO_AM_I lido | Grandezas | Status |
|---|---|---|---|---|
| `0x5F` | **HTS221** | `0xBC` ✅ | Umidade relativa, temperatura | Livre |
| `0x5C` | **LPS25H** | `0xBD` ✅ | Pressão barométrica, temperatura | Livre |
| `0x39` | **TCS3400** | `0x90` ✅ | Luminosidade + cor (RGBC) | Livre |
| `0x6A` | LSM9DS1 (accel/gyro) | `0x68` ✅ | Aceleração, rotação | Livre |
| `0x1C` | LSM9DS1 (magn) | — | Campo magnético | `UU` (driver do kernel, via IIO) |
| `0x46` | ATtiny88 | — | Joystick + matriz LED 8×8 | Livre (fora do escopo) |

### Leituras reais obtidas na validação

```
HTS221  -> Umidade: 34,7 %rH  | Temperatura: 40,18 °C
LPS25H  -> Pressão: 1007,36 hPa | Temperatura: 39,74 °C
TCS3400 -> Clear: 42  R: 9  G: 20  B: 16
CPU     -> 57,3 °C (/sys/class/thermal/thermal_zone0/temp)
```

**Descoberta crítica:** a temperatura lida (~40 °C) está muito acima da ambiente porque o Sense HAT
fica montado a poucos milímetros do SoC da Pi 5, que opera a 57 °C. Isso é auto-aquecimento — e é o
principal desafio técnico do projeto (ver seção 5).

---

## 2. Definição do sistema

**GreenPi Monitor** — sistema IoT de monitoramento ambiental para apoio ao estudo de plantas.

- **Problema:** não há acompanhamento contínuo nem histórico das condições do ambiente
  (temperatura, umidade, luminosidade, pressão) onde as plantas são mantidas.
- **Solução:** a Raspberry Pi 5 com Android embarcado lê os sensores do Sense HAT via I2C,
  aplica compensação térmica, publica por MQTT numa plataforma de IoT (ThingsBoard) e
  disponibiliza um app Android com dashboard e alertas visuais por cor.

### Arquitetura e fluxo de dados

```
┌──────────────────────────── Raspberry Pi 5 (Android 16 AOSP) ────────────────────────────┐
│                                                                                          │
│   Sense HAT v2 ──I2C (/dev/i2c-1)──> Camada nativa C (JNI, ioctl I2C_SLAVE)               │
│   HTS221 · LPS25H · TCS3400                     │                                        │
│                                                 v                                        │
│   /sys/class/thermal ──CPU temp──>  SenseHatReader (Kotlin)  ──> Compensação térmica      │
│   WifiManager ──RSSI──>                         │                                        │
│                                                 v                                        │
│                                    CollectorService (Foreground Service)                 │
│                                                 │                                        │
└─────────────────────────────────────────────────┼────────────────────────────────────────┘
                                                  │  MQTT (Paho) tcp://<host>:1883
                                                  │  topic: v1/devices/me/telemetry
                                                  v
                              ┌──────────── ThingsBoard CE (Docker) ────────────┐
                              │  Broker MQTT · PostgreSQL · Rule Engine         │
                              │  Dashboard web · Alarmes por threshold          │
                              └────────────────────┬────────────────────────────┘
                                                   │  REST API (HTTPS/JWT)
                                                   v
                              App Android — Tela Dashboard (Pi 5 ou celular)
                              cards coloridos + histórico + status do ambiente
```

**Fluxo:** sensor → I2C → JNI → Kotlin → compensação → JSON → MQTT → ThingsBoard →
(persistência + regras) → REST → app dashboard.

### Mapeamento dos pilares do curso

| Pilar | Como é atendido |
|---|---|
| **Sistemas Operacionais** | Android 16 AOSP na Pi 5; acesso a `/dev` e `/sys`; Foreground Service (processo/thread dedicada, wake lock, ciclo de vida) |
| **Android Embarcado / Framework** | Camada nativa em C via NDK/JNI falando I2C por `ioctl(I2C_SLAVE)` — o mesmo caminho de uma HAL |
| **Android Básico e Ferramentas** | App Kotlin, Activities, SharedPreferences, ViewBinding, build Gradle, depuração por adb (Wireless Debugging) |
| **Redes de Computadores** | MQTT (publish, QoS 1, keep-alive) para uplink; HTTP/REST + JWT para consulta do histórico |
| **Conectividade sem fio** | Wi-Fi 802.11ac 5 GHz; RSSI coletado como telemetria de qualidade de enlace |
| **Plataformas de IoT** | ThingsBoard CE: broker MQTT, persistência, dashboard web, alarmes |

---

## 3. Telemetria publicada

Tópico `v1/devices/me/telemetry`, QoS 1, payload JSON:

```json
{
  "temperature":     26.4,    // °C — compensada (valor principal)
  "temperature_raw": 40.2,    // °C — HTS221 sem compensação
  "humidity":        34.7,    // %rH
  "pressure":        1007.4,  // hPa
  "light":           42,      // canal clear do TCS3400
  "cpu_temp":        57.3,    // °C — usado na compensação
  "wifi_rssi":       -57,     // dBm
  "status":          "OK"     // OK | ATENCAO | ALERTA
}
```

**Regra de status (faixas para cultivo de plantas de interior):**

| Grandeza | OK (verde) | Atenção (amarelo) | Alerta (vermelho) |
|---|---|---|---|
| Temperatura | 18–28 °C | 15–18 / 28–32 °C | < 15 ou > 32 °C |
| Umidade | 40–70 %rH | 30–40 / 70–80 %rH | < 30 ou > 80 %rH |
| Luminosidade | > 100 | 30–100 | < 30 |

---

## 4. Estrutura da entrega

```
Entrega/
├── Codigo/                       → vira o .zip da entrega
│   ├── app-android/              App Kotlin (GreenPi Monitor)
│   ├── firmware/                 Camada nativa C (I2C) + scripts de bring-up com i2c-tools
│   ├── plataforma/               docker-compose do ThingsBoard, script de provisionamento,
│   │                             dashboard exportado (.json)
│   ├── midia/                    vídeos e imagens da demonstração
│   └── README.md                 instalação, configuração e ordem de execução
├── StatusReport1.pptx            Arquitetura e Desenvolvimento
├── StatusReport2.pptx            Resultados e Integração
├── RelatorioFinal.docx           Relatório no template oficial
└── Outros/                       diagramas, evidências de teste, referências
```

---

## 5. Desafios técnicos identificados e soluções

| # | Desafio | Solução adotada |
|---|---|---|
| 1 | Android na Pi 5 **não expõe HAL de sensores** — `SensorManager` retorna vazio | Acesso direto ao barramento via `/dev/i2c-1` com camada nativa C (JNI), dispensando driver de kernel |
| 2 | Drivers do kernel para HTS221/LPS25H **não fizeram bind** (`waiting_for_supplier`), impedindo uso do subsistema IIO | Implementação em espaço de usuário: leitura dos registradores e aplicação das curvas de calibração de fábrica direto do chip |
| 3 | **Auto-aquecimento**: sensor lê ~40 °C com ambiente ~26 °C, por estar sobre o SoC a 57 °C | Compensação `T_real = T_sensor − (T_cpu − T_sensor) / k`, com `k` calibrável na tela de configuração; publica-se também o valor bruto para auditoria |
| 4 | HTS221 exige **calibração por interpolação linear** com 8 coeficientes de fábrica | Coeficientes lidos uma única vez no início e mantidos em memória |
| 5 | Um único APK precisa rodar na Pi 5 (com sensores) e no celular (sem sensores) | Detecção em runtime de `/dev/i2c-1`: com Sense HAT → modo Coletor + Dashboard; sem → modo Dashboard |
| 6 | Credenciais não podem ir versionadas no código | `config.example.properties` + configuração em tempo de execução (SharedPreferences) |

---

## 6. Fases de execução

| Fase | Entrega | Estado |
|---|---|---|
| 0 | Levantamento e validação do hardware | ✅ concluída |
| 1 | ThingsBoard CE em Docker + device + dashboard web | ✅ concluída |
| 2 | Camada nativa I2C (JNI) + leitura dos 3 sensores | ✅ concluída |
| 3 | Foreground Service + publicação MQTT | ✅ concluída |
| 4 | Tela de Dashboard no app (REST) + alertas por cor | ✅ concluída |
| 5 | Testes end-to-end e coleta de evidências | ✅ concluída |
| 6 | Status Report 1, Status Report 2, Relatório Final e .zip | ✅ concluída |
