# Plataforma de IoT — ThingsBoard

Esta pasta contem tudo o que e necessario para subir e configurar a plataforma
que recebe, armazena e exibe a telemetria do GreenPi Monitor.

## Arquivos

| Arquivo | Funcao |
|---|---|
| `docker-compose.yml` | Sobe o ThingsBoard CE (broker MQTT + PostgreSQL + dashboard web) |
| `provisionar_thingsboard.py` | Cria o dispositivo, imprime o token de acesso e monta o dashboard |
| `dashboard_greenpi.json` | Dashboard exportado, para importar manualmente pela interface |
| `serie_timeseries_template.json` | Modelo de configuracao de serie usado pelo script |
| `config.example.env` | Modelo das variaveis de ambiente (sem segredos) |

## Passo a passo

### 1. Subir a plataforma

```bash
docker compose up -d
docker compose logs -f     # aguarde "Started ThingsboardServerApplication"
```

A primeira execucao instala o banco e leva alguns minutos. Quando terminar, a
interface fica em `http://localhost:8080` (usuario `tenant@thingsboard.org`,
senha `tenant`).

### 2. Provisionar o dispositivo e o dashboard

```bash
cp config.example.env .env
# edite o .env com o IP da sua maquina e a senha do tenant
set -a && . ./.env && set +a
python3 provisionar_thingsboard.py
```

O script e idempotente e, ao final, imprime os valores que devem ser digitados
na tela de Configuracoes do aplicativo Android:

```
Host ......................: 192.168.0.8
Porta MQTT ................: 1883
Porta HTTP ................: 8080
Token de acesso ...........: <token gerado pela plataforma>
Nome do dispositivo .......: GreenPi-RPi5
```

### 3. Descobrir o IP da maquina que roda a plataforma

A Raspberry Pi precisa alcancar o servidor pela rede local, entao use o IP da
maquina, nunca `localhost`:

```bash
ipconfig getifaddr en0     # macOS
hostname -I                # Linux
```

## Telemetria recebida

O aplicativo publica em `v1/devices/me/telemetry`, com QoS 1:

| Chave | Unidade | Descricao |
|---|---|---|
| `temperature` | °C | Temperatura ambiente ja compensada |
| `temperature_raw` | °C | Leitura direta do HTS221, sem compensacao |
| `humidity` | %rH | Umidade relativa |
| `pressure` | hPa | Pressao barometrica |
| `light` | contagens | Canal "clear" do TCS3400 (indice de luminosidade) |
| `cpu_temp` | °C | Temperatura do SoC, usada na compensacao |
| `wifi_rssi` | dBm | Potencia do sinal Wi-Fi |
| `status` | texto | `OK`, `ATENCAO` ou `ALERTA` |

## Importar o dashboard manualmente

Caso prefira nao usar o script:
**Dashboards → Import dashboard → `dashboard_greenpi.json`**. Depois, ajuste o
alias de entidade para apontar para o seu dispositivo.

## Observacoes

- Os graficos de serie temporal do ThingsBoard so desenham a curva quando cada
  `dataKey` traz o bloco `settings` completo (tipo da serie, eixo e estilo da
  linha). E por isso que o script usa `serie_timeseries_template.json`.
- O ThingsBoard escuta na porta **9090** dentro do container; o mapeamento
  `8080:9090` do compose e o que expoe a interface na 8080 do host.
- Para parar tudo sem perder os dados: `docker compose down`. Para apagar
  tambem os dados: `docker compose down -v`.
