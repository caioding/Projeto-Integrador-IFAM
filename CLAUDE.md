# GreenPi Monitor

Projeto final do Curso de Android e IoT (Instituto de Pesquisas Eldorado).
Uma Raspberry Pi 5 com Android 16 (AOSP) lê os sensores do Sense HAT por I2C,
compensa o auto-aquecimento da placa, publica por MQTT no ThingsBoard e exibe
tudo num app Android com alerta por cor e histórico.

**Não há Machine Learning no projeto** — foi removido do pitch original por
decisão do autor. Não reintroduzir.

Para o estado atual, pendências e histórico de decisões, ver
[CONTINUIDADE.md](CONTINUIDADE.md).

## Idioma

Responder e escrever em **português do Brasil**.

## Comandos

```bash
# Duas coisas SEMPRE necessárias: há dois dispositivos adb conectados
# (a Pi e o emulador) e o JDK não está no PATH.
export PATH=$PATH:~/Library/Android/sdk/platform-tools
export ANDROID_SERIAL="adb-831a9cd38844814e-WayZxZ._adb-tls-connect._tcp"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Build e instalação
cd Entrega/Codigo/app-android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Plataforma (dados persistem em volumes Docker)
cd Entrega/Codigo/plataforma && docker compose up -d
python3 provisionar_thingsboard.py        # idempotente; imprime o token

# Validar os sensores sem envolver o app
adb shell sh /data/local/tmp/validar_sensores.sh
```

Painel: `http://localhost:8080` — `tenant@thingsboard.org` / `tenant`.
Emulador: `emulator-5554` (AVD "Medium_Phone"). Pi: `192.168.0.18`. Mac: `192.168.0.8`.

## Arquitetura — invariantes

- **Um único APK, dois papéis**, decididos em runtime pela existência de
  `/dev/i2c-1`: com Sense HAT é coletor + painel; sem ele, só painel (REST).
  Não criar variantes de build nem um segundo app.
- **O acesso aos sensores é por camada nativa em C**
  (`app/src/main/cpp/sensehat_i2c.c`, `ioctl(I2C_RDWR)` via JNI). Isso não é
  preferência estética: o `SensorManager` retorna vazio nesta imagem e os
  drivers de kernel do HTS221/LPS25H não completam o bind. Não tentar migrar
  para `SensorManager` nem para IIO.
- **A temperatura publicada é sempre compensada, e a bruta vai junto**
  (`temperature_raw`). O fator `k` é configurável porque depende da montagem.
- **MQTT para uplink, REST para consulta.** Não trocar um pelo outro.

## Hardware — fatos verificados, não presumir diferente

| Endereço I2C | Chip | WHO_AM_I | Uso |
|---|---|---|---|
| `0x5F` | HTS221 | `0xBC` | umidade + temperatura |
| `0x5C` | LPS25H | `0xBD` | pressão + temperatura |
| `0x39` | TCS3400 | `0x90` | luminosidade (canal clear) |
| `0x46` | ATtiny88 | — | joystick e matriz LED — **fora do escopo** |

- `/dev/i2c-1` tem modo `crw-rw-rw-` e o SELinux está **permissive**: um app
  comum abre o barramento sem root.
- Nos sensores da ST, o bit 7 do sub-registrador liga o auto-incremento
  (ex.: `0xA8` lê a partir de `0x28`). No TCS3400 o auto-incremento é
  automático — usar `0x80 | reg`, e **não** `0xA0 | reg`.
- `light` é contagem do canal clear, **não lux**. Não apresentar como lux.

## Convenções

- **Dentro de `Entrega/Codigo/` use apenas ASCII** — código, comentários,
  strings de layout, READMEs e scripts. Escrever "graus C" e não "°C",
  "conversao" e não "conversão". Fora dessa pasta (raiz, `Outros/`), português
  acentuado normal.
- **Nenhum segredo no código.** Token e senhas são digitados na tela de
  Configurações e guardados em SharedPreferences. Manter
  `config.example.properties` e `config.example.env` como modelos.
- Comentários explicam **por quê**, não o quê. Vários comentários existentes
  registram a razão de uma decisão não óbvia — preservá-los ao editar.

## Armadilhas já pagas

- **Não entregar `Entrega/Codigo.zip`** (22 MB, cheio de artefatos de build).
  O pacote correto é `Entrega/GreenPi_Monitor_Codigo.zip` (691 KB). Ambos os
  zips manuais estão no `.gitignore`.
- **Unidades com `%` quebram `String.format`.** Formatar o número em separado e
  concatenar depois — `"%.1f".format(v) + " %rH"`, nunca a unidade dentro da
  string de formato.
- **Widget de série temporal do ThingsBoard exige `settings` completo por
  `dataKey`** (tipo da série, eixo, estilo). Sem isso a curva simplesmente não
  aparece, sem erro. Modelo em `plataforma/serie_timeseries_template.json`.
- **O painel web do ThingsBoard 4.2.1.1 tem um bug de WebSocket**
  (`aggregationMap.aggMap`) que pode exigir atualizar a página. Já investigado
  a fundo, não é configuração nossa, e o autor decidiu deixar assim.
  Não reabrir.
- **Não há LibreOffice nesta máquina.** Não é possível renderizar `.pptx`/`.docx`
  para inspeção visual; a verificação foi feita geometricamente com
  `python-pptx`.
- Preferências vêm de `SharedPreferences` como `Float`; converter para `Double`
  sem arredondar produz coisas como `1.600000023841858` na tela.

## Ao mexer no app

Depois de qualquer mudança, instalar e conferir **nos dois dispositivos** — a Pi
(modo coletor) e o emulador (modo painel). Vários defeitos só apareceram em
execução real, nenhum apareceria em análise estática.
