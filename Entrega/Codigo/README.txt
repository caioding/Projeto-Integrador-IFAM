GreenPi Monitor - Projeto Final
Curso de Android e Internet das Coisas (IoT) - Instituto de Pesquisas Eldorado
Autor: Caio Cesar

O QUE E ESTE PROJETO
--------------------
Sistema IoT de monitoramento ambiental para apoio ao estudo de plantas.
Uma Raspberry Pi 5 com Android 16 (AOSP) le os sensores do Sense HAT pelo
barramento I2C, compensa o auto-aquecimento da placa, publica a telemetria por
MQTT no ThingsBoard e exibe as condicoes num aplicativo Android com alertas por
cor e historico em graficos.

CONTEUDO DO ARQUIVO
-------------------
  /app-android      Aplicativo Android em Kotlin + camada nativa em C (JNI/NDK)
  /firmware         Documentacao do acesso de baixo nivel e script de validacao
  /plataforma       ThingsBoard em Docker, provisionamento e dashboard
  /midia            Imagens e videos da demonstracao
  README.md         Documentacao completa (leia este primeiro)
  DEPENDENCIAS.md   Bibliotecas, ferramentas e versoes utilizadas

ORDEM CORRETA DE EXECUCAO
-------------------------
  1. Suba a plataforma:
       cd plataforma && docker compose up -d
       (aguarde alguns minutos na primeira execucao)

  2. Provisione o dispositivo e o dashboard:
       cp config.example.env .env      # ajuste o IP e a senha
       set -a && . ./.env && set +a
       python3 provisionar_thingsboard.py
       (anote o token de acesso impresso ao final)

  3. Compile e instale o aplicativo:
       cd ../app-android
       cp local.properties.example local.properties   # ajuste o caminho do SDK
       ./gradlew assembleDebug
       adb install -r app/build/outputs/apk/debug/app-debug.apk

  4. Configure o aplicativo na Raspberry Pi:
       Abra o app -> Configuracoes -> preencha host, portas, token e nome do
       dispositivo. Use "Testar conexao MQTT" para confirmar.

  5. Inicie a coleta:
       Botao "Iniciar coleta" na tela principal.

CHAVES E SENHAS
---------------
Nenhuma credencial esta gravada no codigo-fonte. Os valores sao informados em
tempo de execucao e guardados em SharedPreferences. Os modelos estao em:
  app-android/config.example.properties
  plataforma/config.example.env

REQUISITOS
----------
  JDK 17+, Android SDK 34, NDK 30.0.14904198, CMake 3.22.1
  Docker 20.10+ e Docker Compose v2
  Python 3.9+ (apenas biblioteca padrao)

Detalhes completos em README.md e DEPENDENCIAS.md.
