# GreenPi Monitor — resumo de continuidade

> Documento de handoff. Cole o conteúdo (ou aponte para este arquivo) ao iniciar
> um chat novo. Última atualização: 03/09/2026.

---

## 1. O que é o projeto

Projeto final do **Curso de Android e Internet das Coisas (IoT)** — Instituto de
Pesquisas Eldorado. Autor: **Caio Cesar**.

**GreenPi Monitor** — sistema IoT de monitoramento ambiental para apoio ao estudo
de plantas. Uma Raspberry Pi 5 com Android 16 (AOSP) lê os sensores do Sense HAT
pelo barramento I2C, compensa o auto-aquecimento da placa, publica a telemetria
por MQTT no ThingsBoard e exibe as condições num aplicativo Android com alertas
por cor e histórico em gráficos.

O escopo de Machine Learning que existia no pitch original **foi removido** por
decisão do autor. Não há ML no projeto.

- **Diretório:** `/Users/caiocesar/StudioProjects/FinalProject`
- **Repositório:** `https://github.com/caioding/greenpi-monitor` (privado, já publicado)
- **Commit atual:** `d573ad4`, branch `main`, árvore limpa

---

## 2. Hardware — tudo validado em bancada, não é suposição

| Item | Valor confirmado |
|---|---|
| Placa | Raspberry Pi 5 Model B Rev 1.1, 8 GB, SoC BCM2712 |
| SO | Android 16 (AOSP `aosp_rpi5`), SDK 36, arm64-v8a, build **userdebug** |
| SELinux | **Permissive** (`ro.debuggable=1`) |
| Módulo | Raspberry Pi Sense HAT rev. 2 |
| Display | Touch Display 2 (7"), touch Goodix gt911 |
| Rede | Wi-Fi 802.11ac 5 GHz, **192.168.0.18** |
| Serial adb | `adb-831a9cd38844814e-WayZxZ._adb-tls-connect._tcp` (Wireless Debugging) |
| Emulador | `emulator-5554` — AVD "Medium_Phone", Android 16 (SDK 36) |
| Mac (host) | **192.168.0.8** |

### Sensores no barramento I2C (`/dev/i2c-1`, modo `crw-rw-rw-`)

| Endereço | Chip | WHO_AM_I lido | Uso no projeto |
|---|---|---|---|
| `0x5F` | HTS221 | `0xBC` | Umidade + temperatura — **usado** |
| `0x5C` | LPS25H | `0xBD` | Pressão + temperatura — **usado** |
| `0x39` | TCS3400 | `0x90` | Luminosidade RGBC — **usado** |
| `0x6A` | LSM9DS1 | `0x68` | Acel/giro — não usado |
| `0x1C` | LSM9DS1 magn | `UU` | Reservado pelo kernel (IIO) |
| `0x46` | ATtiny88 | — | Joystick + matriz LED — **fora do escopo** (decisão do autor) |

### Duas descobertas que definiram a arquitetura

1. **Não existe HAL de sensores.** `dumpsys sensorservice` → *"No Sensors on the
   device"*. O `SensorManager` não enxerga nada.
2. **Os drivers de kernel do HTS221 e do LPS25H não completam o bind**
   (`waiting_for_supplier` em `/sys/bus/i2c/devices/`), então o IIO também não
   os expõe.

Por isso o acesso é direto ao barramento, com camada nativa em C.

---

## 3. Arquitetura

```
Sense HAT ──I2C──> libsensehat.so (C, ioctl I2C_RDWR, JNI/NDK)
                        │
                        v
                   SenseHat.kt  ──> compensação térmica ──> classificação
                        │              (usa /sys/class/thermal)
                        v
                 CollectorService (foreground, thread própria, wake lock)
                        │  MQTT QoS 1, a cada 10 s
                        │  tópico v1/devices/me/telemetry
                        v
             ThingsBoard CE (Docker) — broker + PostgreSQL + dashboard
                        │  REST + JWT
                        v
              App Android em modo painel (Pi 5 ou celular)
```

**Um único APK, dois papéis**, decididos em runtime: se `/dev/i2c-1` existe →
coletor + painel; se não existe → apenas painel (consulta REST a cada 10 s).

### Telemetria publicada

```json
{"temperature":26.57,"temperature_raw":36.70,"humidity":45.8,
 "pressure":1008.69,"light":702,"cpu_temp":52.9,"wifi_rssi":-52,"status":"OK"}
```

`light` é o **canal clear em contagens**, não lux — não há calibração em lux.

---

## 4. Estrutura da entrega

```
FinalProject/
├── CONTINUIDADE.md          ← este arquivo
├── PLANEJAMENTO.md          Levantamento técnico inicial
├── README.md                Visão geral (raiz do repositório)
├── Docs/                    PDFs do curso (instruções e material)
├── SimpleMQTT/              Exemplo do curso (referência; fora do git)
└── Entrega/
    ├── Codigo/                        → vira o .zip da entrega
    │   ├── app-android/               App Kotlin + cpp/sensehat_i2c.c
    │   ├── firmware/                  README + validar_sensores.sh
    │   ├── plataforma/                docker-compose, provisionar_thingsboard.py,
    │   │                              dashboard_greenpi.json, config.example.env
    │   ├── midia/                     Capturas de tela
    │   ├── README.md · README.txt · DEPENDENCIAS.md
    ├── Outros/
    │   ├── diagramas/                 arquitetura, fluxo_dados, hardware (.svg + .png)
    │   ├── evidencias/                5 capturas de tela
    │   ├── anotacoes/PLANEJAMENTO.md
    │   └── roteiro_apresentacao.md    Roteiro cronometrado do vídeo de 15 min
    ├── StatusReport1_Arquitetura_e_Desenvolvimento.pptx   (13 slides)
    ├── StatusReport2_Resultados_e_Integracao.pptx         (9 slides)
    ├── RelatorioFinal_GreenPi_Monitor.docx                (~4.700 palavras)
    └── GreenPi_Monitor_Codigo.zip                         (691 KB — o correto)
```

### Atenção ao empacotar

Existem `Entrega/Codigo.zip` (22 MB) e `Entrega/Outros.zip` criados manualmente
no Finder. O `Codigo.zip` contém **2.203 entradas de artefatos de build** e
**não deve ser entregue**. O pacote válido é `GreenPi_Monitor_Codigo.zip`
(691 KB, 94 arquivos), testado: extraído do zero e com o `local.properties`
ajustado, compila com `BUILD SUCCESSFUL`. Ambos os zips manuais estão no
`.gitignore`.

---

## 5. Estado — o que está pronto e verificado

| Item | Estado |
|---|---|
| Leitura dos 3 sensores por I2C | ✅ WHO_AM_I confirmado em hardware |
| Camada nativa C (JNI/NDK) | ✅ `libsensehat.so` para arm64-v8a e x86_64 |
| Compensação térmica | ✅ 36,7 °C brutos → 26,6 °C, com SoC a 52,9 °C |
| Serviço de coleta + MQTT | ✅ publicando a cada 10 s, QoS 1 |
| ThingsBoard + dashboard | ✅ 6 cartões + 4 gráficos, provisionado por script |
| App na Pi 5 (coletor) | ✅ |
| App no celular/emulador (painel) | ✅ |
| Status Report 1 e 2 (.pptx) | ✅ validados, sem transbordo |
| Relatório Final (.docx) | ✅ segue o template, 9 imagens, 7 tabelas |
| Zip da entrega | ✅ compila do zero |
| Roteiro do vídeo de 15 min | ✅ |
| Repositório privado no GitHub | ✅ publicado |

### Defeitos encontrados e corrigidos durante os testes

1. Crash ao abrir o Histórico — a unidade `%rH` entrava na string de
   `String.format` e o `%r` era lido como especificador inválido.
2. Rótulo do valor mais recente desenhado sobre a curva do gráfico — movido para
   o título da seção.
3. Fator k exibido como `1.600000023841858` — arredondamento na leitura da
   preferência (Float → Double).
4. Gráficos vazios no dashboard do ThingsBoard — cada `dataKey` precisa do bloco
   `settings` completo (tipo da série, eixo, estilo). O modelo está em
   `plataforma/serie_timeseries_template.json`.
5. Token do dispositivo visível na captura da tela de Configurações — tapado, e
   o relatório foi regerado.

---

## 6. Ambiente — como voltar a rodar

```bash
# Plataforma (ThingsBoard já provisionado, dados persistem nos volumes)
docker ps --filter name=thingsboard
# se estiver parado:
cd Entrega/Codigo/plataforma && docker compose up -d

# Painel web
open http://localhost:8080      # tenant@thingsboard.org / tenant

# Dispositivo
export PATH=$PATH:~/Library/Android/sdk/platform-tools
export ANDROID_SERIAL="adb-831a9cd38844814e-WayZxZ._adb-tls-connect._tcp"
adb devices

# Compilar e instalar
cd Entrega/Codigo/app-android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Validar os sensores pelo terminal
adb shell sh /data/local/tmp/validar_sensores.sh
```

- **Device no ThingsBoard:** `GreenPi-RPi5` — o token é impresso por
  `python3 provisionar_thingsboard.py`, ou está em *Entities → Devices →
  Manage credentials*. **Não está versionado em lugar nenhum.**
- **Dashboard:** `http://localhost:8080/dashboards/f6c4d620-a785-11f1-8b01-45aa857c5248`
- O dashboard também está publicado em link público (sem login) — útil na
  apresentação; o `publicId` aparece ao rodar o script com `TB_PUBLIC=1`.
- A validade do token JWT do ThingsBoard foi aumentada de 2,5 h para **12 h**,
  para não expirar durante a apresentação.

### Toolchain

JDK 21 (JBR do Android Studio) · Gradle 8.7 · AGP 8.5.2 · Kotlin 1.9.24 ·
compileSdk 34 · NDK 30.0.14904198 · CMake 3.22.1 · Docker 29 ·
ThingsBoard 4.2.1.1.

**Não há LibreOffice no Mac** — a inspeção visual de .pptx/.docx foi feita
geometricamente com `python-pptx` (script em scratchpad), não por renderização.

---

## 7. Limitação conhecida, decidida como "deixar assim"

O painel web do ThingsBoard 4.2.1.1 lança
`TypeError: undefined is not an object (evaluating 'this.aggregationMap.aggMap')`
no caminho de atualização em tempo real, o que derruba o WebSocket em laço.

**Investigado a fundo:** não é a configuração do dashboard nem o tipo de
agregação — reproduzido com `AVG` e com `NONE`. Comandos WebSocket enviados
manualmente com token novo funcionam e a conexão se mantém. É bug do cliente
Angular do ThingsBoard.

**Efeito prático:** os dados chegam e são armazenados corretamente (confirmado
por REST e pelo app); o painel web pode exigir **atualizar a página** para
mostrar o valor mais recente. Está registrado na seção 5.2 do relatório.
Decisão do autor: **não perseguir mais**.

---

## 8. Pendências

### 8.1 Faixas por espécie — aprovado, falta implementar

O autor **gostou das três opções** apresentadas. Faixas de referência para as
culturas mais comuns de Manaus:

**Mandioca** (*Manihot esculenta*) — a mais cultivada no Amazonas

| Grandeza | OK | Atenção | Alerta |
|---|---|---|---|
| Temperatura | 25 – 29 °C | 20 – 25 ou 29 – 33 °C | < 20 ou > 33 °C |
| Umidade | 60 – 85 %rH | 50 – 60 ou 85 – 95 %rH | < 50 ou > 95 %rH |

**Cupuaçu** (*Theobroma grandiflorum*)

| Grandeza | OK | Atenção | Alerta |
|---|---|---|---|
| Temperatura | 22 – 28 °C | 20 – 22 ou 28 – 32 °C | < 20 ou > 32 °C |
| Umidade | 75 – 90 %rH | 65 – 75 ou 90 – 95 %rH | < 65 ou > 95 %rH |

**Banana** (*Musa* spp.): 26 – 28 °C, umidade acima de 60 %rH, pleno sol.

**Faixas atuais no código** (plantas de interior, em `Environment.kt`):
temperatura 18–28 °C, umidade 40–70 %rH, luminosidade acima de 100 contagens.

Proposta feita e ainda **não implementada**: um seletor de espécie na tela de
Configurações, para alternar entre os perfis sem recompilar.

Três ressalvas registradas:
1. Os números são **referências agronômicas gerais**, sem fonte específica
   verificada. Confirmar com a **Embrapa Amazônia Ocidental** (fica em Manaus).
2. A faixa de luminosidade **não é convertível** — o sensor entrega contagens,
   não lux. Os limiares precisam ser medidos no local (meio-dia × sombra).
3. O ambiente de teste está em torno de 45 %rH e marcaria ALERTA o tempo todo
   nas faixas de Manaus. Vale explicar isso na apresentação ou manter as faixas
   de interior na demo.

### 8.2 Outras pendências

- **Calibrar o fator k** comparando com termômetro de referência ao longo de um
  dia. Está em 1,6, o que dá resultado plausível, mas não foi validado contra
  instrumento.
- **Gravar o vídeo** de demonstração, seguindo `Outros/roteiro_apresentacao.md`,
  e preencher os links no Anexo C do relatório.
- **Alarmes por limiar** no motor de regras do ThingsBoard — planejado, não feito.
- Apagar `Entrega/Codigo.zip` e `Entrega/Outros.zip` para não entregar o errado.

---

## 9. Preferências de trabalho observadas

- Responder e escrever tudo em **português do Brasil**.
- O autor pede **simplicidade**: escopo enxuto, desde que atenda aos requisitos.
- Ele **valida no hardware real** antes de aceitar — testar de fato, não presumir.
- Quando algo não vale o esforço, ele diz para deixar como está (foi o caso do
  bug de WebSocket). Respeitar essa decisão.
- Ele já publica no GitHub por conta própria (usou Codex); não assumir que a
  publicação é minha tarefa.
