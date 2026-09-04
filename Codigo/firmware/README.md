# Codigo embarcado (firmware)

Neste projeto o dispositivo embarcado nao e um microcontrolador com firmware
proprio: e uma **Raspberry Pi 5 rodando Android 16 (AOSP)**. O papel que caberia
ao firmware — falar com os sensores no nivel dos registradores — e cumprido por
uma **camada nativa em C**, compilada com o NDK e carregada pelo aplicativo.

## Onde esta o codigo de baixo nivel

| Arquivo | Descricao |
|---|---|
| `../app-android/app/src/main/cpp/sensehat_i2c.c` | Abre `/dev/i2c-1` e executa as transacoes com `ioctl(I2C_RDWR)` |
| `../app-android/app/src/main/cpp/CMakeLists.txt` | Compilacao da biblioteca `libsensehat.so` |
| `../app-android/app/src/main/java/com/greenpi/monitor/SenseHat.kt` | Mapa de registradores e conversao para unidades de engenharia |
| `validar_sensores.sh` | Verificacao do hardware pelo terminal, com `i2c-tools` |

## Por que acesso direto ao barramento

Duas limitacoes da imagem AOSP usada no kit levaram a essa decisao:

1. **Nao existe HAL de sensores.** `dumpsys sensorservice` responde
   *"No Sensors on the device"*, entao `SensorManager` nao enxerga nada.
2. **Os drivers de kernel do HTS221 e do LPS25H nao completam o bind.** Os
   dispositivos aparecem em `/sys/bus/i2c/devices/` com o estado
   `waiting_for_supplier`, e por isso tambem nao ha nada exposto via IIO.

A saida foi conversar direto com o barramento. Isso e possivel sem privilegios
de root porque `/dev/i2c-1` tem modo `crw-rw-rw-` nesta imagem.

## Mapa dos sensores (confirmado com `i2cdetect -y 1`)

| Endereco | Chip | WHO_AM_I | Grandezas |
|---|---|---|---|
| `0x5F` | HTS221 | `0xBC` | Umidade relativa e temperatura |
| `0x5C` | LPS25H | `0xBD` | Pressao barometrica e temperatura |
| `0x39` | TCS3400 | `0x90` | Luminosidade e cor (RGBC) |
| `0x6A` | LSM9DS1 | `0x68` | Acelerometro e giroscopio (nao usado) |
| `0x1C` | LSM9DS1 | — | Magnetometro (reservado pelo kernel) |
| `0x46` | ATtiny88 | — | Joystick e matriz de LED (fora do escopo) |

## Sequencia de inicializacao

```
HTS221   AV_CONF   (0x10) = 0x1B   media de 32 amostras de umidade, 16 de temperatura
         CTRL_REG1 (0x20) = 0x85   ligado, BDU habilitado, 1 Hz
         calibracao (0x30..0x3F)   16 coeficientes de fabrica, lidos uma unica vez

LPS25H   CTRL_REG1 (0x20) = 0x94   ligado, BDU habilitado, 1 Hz

TCS3400  ATIME     (0x81) = 0xC0   integracao de aproximadamente 178 ms
         CONTROL   (0x8F) = 0x02   ganho de 16x, adequado a ambiente interno
         ENABLE    (0x80) = 0x03   PON + AEN
```

Nos chips da ST, ligar o bit 7 do endereco do sub-registrador habilita o
auto-incremento, o que permite ler varios registradores numa unica transacao
(por exemplo, `0xA8` le de `0x28` em diante).

## Conversoes aplicadas

```
HTS221 (interpolacao linear entre dois pontos calibrados de fabrica):
    umidade     = H0 + (H1 - H0) * (H_out - H0_T0_OUT) / (H1_T0_OUT - H0_T0_OUT)
    temperatura = T0 + (T1 - T0) * (T_out - T0_OUT)    / (T1_OUT   - T0_OUT)

LPS25H:
    pressao     = valor_bruto_24_bits / 4096          [hPa]
    temperatura = 42.5 + valor_bruto_16_bits / 480    [graus C]

TCS3400:
    luminosidade = canal "clear" (16 bits), indice proporcional a iluminancia
```

## Validacao pelo terminal

```bash
adb push validar_sensores.sh /data/local/tmp/
adb shell sh /data/local/tmp/validar_sensores.sh
```

O script varre o barramento, confere a identidade de cada chip pelo WHO_AM_I,
liga os sensores e imprime os bytes brutos. E o primeiro passo para separar um
defeito de hardware de um problema no aplicativo.

## Compilacao

A camada nativa e compilada junto com o aplicativo:

```bash
cd ../app-android && ./gradlew assembleDebug
```

Requer **NDK 30.0.14904198** e **CMake 3.22.1**. A saida `libsensehat.so` e
gerada para `arm64-v8a` (Raspberry Pi 5 e emuladores em Apple Silicon) e
`x86_64` (emuladores em maquinas Intel).
