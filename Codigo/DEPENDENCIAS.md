# Dependências e bibliotecas do projeto

Lista completa do que é necessário para compilar e executar o GreenPi Monitor.

## 1. Aplicativo Android (`app-android/`)

Declaradas em `app-android/app/build.gradle` e baixadas automaticamente pelo Gradle.

| Biblioteca | Versão | Função |
|---|---|---|
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3` | 1.2.5 | Cliente MQTT usado para publicar a telemetria |
| `androidx.core:core-ktx` | 1.13.1 | Extensões Kotlin do framework |
| `androidx.appcompat:appcompat` | 1.7.0 | Activities compatíveis com versões antigas |
| `com.google.android.material:material` | 1.12.0 | Cartões, botões e campos de texto |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Layouts |
| `junit:junit` | 4.13.2 | Testes unitários |

A camada nativa (`app/src/main/cpp/sensehat_i2c.c`) usa apenas a **libc do
Android (Bionic)** e o **liblog** do NDK. Não há dependência externa.

## 2. Ferramentas de compilação

| Ferramenta | Versão usada | Observação |
|---|---|---|
| JDK | 21 (Android Studio JBR) | Mínimo suportado: 17 |
| Gradle | 8.7 | Via wrapper (`./gradlew`), baixado automaticamente |
| Android Gradle Plugin | 8.5.2 | |
| Kotlin | 1.9.24 | |
| Android SDK Platform | 34 | `compileSdk` e `targetSdk` |
| Android Build Tools | 34.0.0 ou superior | |
| NDK | 30.0.14904198 | Compila `libsensehat.so` |
| CMake | 3.22.1 | Build da camada nativa |

## 3. Plataforma de IoT (`plataforma/`)

| Item | Versão | Função |
|---|---|---|
| Docker Engine | 20.10 ou superior | Executa a plataforma |
| Docker Compose | v2 | Orquestra o container |
| Imagem `thingsboard/tb-postgres` | latest (testado com 4.2.1.1) | Broker MQTT + PostgreSQL + dashboard |

## 4. Script de provisionamento (`plataforma/provisionar_thingsboard.py`)

Requer **Python 3.9 ou superior**. Usa somente a biblioteca padrão
(`json`, `os`, `sys`, `uuid`, `urllib`), portanto **não há pacotes a instalar**.
Ver `plataforma/requirements.txt`.

## 5. Ferramentas no dispositivo embarcado

Já presentes na imagem AOSP do kit:

| Ferramenta | Uso |
|---|---|
| `i2c-tools` (`i2cdetect`, `i2cget`, `i2cset`, `i2ctransfer`) | Validação do hardware (`firmware/validar_sensores.sh`) |
| `adb` (Wireless Debugging) | Instalação do APK e depuração |

## 6. Como instalar tudo

```bash
# 1. Ferramentas Android: instale o Android Studio e, no SDK Manager,
#    marque "Android SDK Platform 34", "NDK 30.0.14904198" e "CMake 3.22.1".

# 2. Plataforma
cd plataforma && docker compose up -d

# 3. Aplicativo (as bibliotecas são baixadas na primeira compilação)
cd ../app-android && ./gradlew assembleDebug
```
