#!/usr/bin/env python3
"""
Provisionamento do ThingsBoard para o projeto GreenPi Monitor.

O script e idempotente: pode ser executado quantas vezes for necessario.
Ele cria (ou reaproveita) o dispositivo, imprime o token de acesso usado pelo
aplicativo Android e monta o dashboard web com os widgets do projeto.

Uso:
    python3 provisionar_thingsboard.py                  # usa os padroes locais
    TB_URL=http://192.168.0.8:8080 python3 provisionar_thingsboard.py

Variaveis de ambiente aceitas (ver config.example.env):
    TB_URL       endereco base do ThingsBoard      (padrao http://localhost:8080)
    TB_USER      usuario administrador do tenant   (padrao tenant@thingsboard.org)
    TB_PASSWORD  senha do usuario                  (padrao tenant)
    TB_DEVICE    nome do dispositivo               (padrao GreenPi-RPi5)
    TB_PUBLIC    "1" para publicar o dashboard sem exigir login
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

TB_URL = os.environ.get("TB_URL", "http://localhost:8080").rstrip("/")
TB_USER = os.environ.get("TB_USER", "tenant@thingsboard.org")
TB_PASSWORD = os.environ.get("TB_PASSWORD", "tenant")
DEVICE_NAME = os.environ.get("TB_DEVICE", "GreenPi-RPi5")
DASHBOARD_TITLE = "GreenPi Monitor - Ambiente"
MAKE_PUBLIC = os.environ.get("TB_PUBLIC", "0") == "1"
USE_DASHBOARD_TIMEWINDOW = os.environ.get("TB_DASH_TW", "1") == "1"

_jwt = None

# Configuracao de serie exigida pelo widget "time_series_chart". O widget so
# desenha a curva quando o dataKey traz este bloco completo (tipo da serie,
# eixo, estilo da linha). O modelo foi extraido de um widget criado pela
# propria interface do ThingsBoard e fica em serie_timeseries_template.json.
_SERIES_TEMPLATE_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                     "serie_timeseries_template.json")
with open(_SERIES_TEMPLATE_PATH, encoding="utf-8") as _f:
    SERIES_SETTINGS_TEMPLATE = json.load(_f)


def api(path, method="GET", body=None):
    """Chamada autenticada a API REST do ThingsBoard."""
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if _jwt:
        headers["X-Authorization"] = "Bearer " + _jwt
    req = urllib.request.Request(TB_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            text = r.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{method} {path} -> HTTP {e.code}: {e.read().decode()[:400]}") from None


def login():
    global _jwt
    resp = api("/api/auth/login", "POST", {"username": TB_USER, "password": TB_PASSWORD})
    _jwt = resp["token"]
    print(f"[ok] autenticado em {TB_URL} como {TB_USER}")


def ensure_device():
    """Cria o dispositivo se ainda nao existir e devolve (id, token de acesso)."""
    query = urllib.parse.urlencode({"deviceName": DEVICE_NAME})
    try:
        device = api(f"/api/tenant/devices?{query}")
        print(f"[ok] dispositivo '{DEVICE_NAME}' ja existia")
    except RuntimeError:
        device = api("/api/device", "POST", {
            "name": DEVICE_NAME,
            "type": "environment-sensor",
            "label": "Raspberry Pi 5 + Sense HAT",
        })
        print(f"[ok] dispositivo '{DEVICE_NAME}' criado")
    device_id = device["id"]["id"]
    token = api(f"/api/device/{device_id}/credentials")["credentialsId"]
    return device_id, token


# --------------------------------------------------------------- dashboard

def widget_catalog():
    """Mapeia o FQN de cada widget usado para a sua definicao no servidor."""
    wanted = {
        "indoor_temperature_card", "indoor_humidity_card",
        "indoor_illuminance_card", "cards.value_card", "time_series_chart",
    }
    listing = api("/api/widgetTypes?pageSize=1000&page=0")["data"]
    found = {w["fqn"]: w["id"]["id"] for w in listing if w["fqn"] in wanted}
    missing = wanted - found.keys()
    if missing:
        raise RuntimeError(f"widgets nao encontrados no servidor: {missing}")
    return {fqn: api(f"/api/widgetType/{wid}") for fqn, wid in found.items()}


def build_configuration(device_id, catalog):
    alias_id = str(uuid.uuid4())
    widgets, layout = {}, {}

    def default_config(fqn):
        raw = catalog[fqn]["descriptor"]["defaultConfig"]
        return json.loads(raw) if isinstance(raw, str) else json.loads(json.dumps(raw))

    def data_key(name, label, color, units, decimals, seed, series=False):
        # A estrutura reproduz integralmente a de um dataKey criado pela
        # interface do ThingsBoard: campos ausentes fazem a serie nao ser
        # desenhada, sem qualquer mensagem de erro.
        settings = {}
        if series:
            settings = json.loads(json.dumps(SERIES_SETTINGS_TEMPLATE))
        return {
            "name": name, "type": "timeseries", "label": label, "color": color,
            "settings": settings, "units": units, "decimals": decimals,
            "_hash": 0.1 + seed * 0.07, "aggregationType": None, "funcBody": None,
            "usePostProcessing": None, "postFuncBody": None,
        }

    def add(fqn, title, keys, size_x, size_y, row, col, units="", decimals=1, icon=None):
        wid = str(uuid.uuid4())
        cfg = default_config(fqn)
        # Datasource ligado diretamente ao dispositivo, no mesmo formato que a
        # interface do ThingsBoard gera. O alias de entidade tambem funciona
        # para os cartoes, mas os graficos de serie temporal so recebem dados
        # com o vinculo direto.
        cfg["datasources"] = [{
            "type": "device", "name": "", "deviceId": device_id,
            "dataKeys": keys,
        }]
        cfg["title"] = title
        cfg["showTitle"] = True
        cfg["units"] = units
        cfg["decimals"] = decimals
        # Todos os graficos seguem a janela de tempo global do dashboard.
        cfg["useDashboardTimewindow"] = USE_DASHBOARD_TIMEWINDOW
        settings = cfg.setdefault("settings", {})
        # Reduz a fonte para que valores como "1008.1 hPa" caibam no cartao.
        if isinstance(settings.get("valueFont"), dict):
            settings["valueFont"]["size"] = 28
        if icon:
            settings["icon"] = icon
        # Desliga a animacao dos graficos: a curva aparece imediatamente ao abrir
        # o painel, o que ajuda tanto na demonstracao quanto na captura de telas.
        if isinstance(settings.get("animation"), dict):
            settings["animation"]["animation"] = False
        descriptor = catalog[fqn]["descriptor"]
        widgets[wid] = {
            "typeFullFqn": "system." + fqn, "type": descriptor.get("type"),
            "sizeX": size_x, "sizeY": size_y, "config": cfg,
            "id": wid, "row": row, "col": col,
        }
        layout[wid] = {"sizeX": size_x, "sizeY": size_y, "row": row, "col": col}

    # Linha 1 - leitura corrente
    add("indoor_temperature_card", "Temperatura",
        [data_key("temperature", "Temperatura", "#E65100", "°C", 1, 0)],
        4, 4, 0, 0, "°C", 1)
    add("indoor_humidity_card", "Umidade",
        [data_key("humidity", "Umidade", "#0277BD", "%", 0, 1)],
        4, 4, 0, 4, "%", 0)
    add("indoor_illuminance_card", "Luminosidade",
        [data_key("light", "Luminosidade", "#F9A825", "", 0, 2)],
        4, 4, 0, 8, "", 0)
    add("cards.value_card", "Pressao atmosferica",
        [data_key("pressure", "Pressao", "#6A1B9A", "hPa", 1, 3)],
        4, 4, 0, 12, "hPa", 1, icon="speed")
    add("cards.value_card", "Status do ambiente",
        [data_key("status", "Status", "#2E7D32", "", 0, 4)],
        4, 4, 0, 16, "", 0, icon="eco")
    add("cards.value_card", "Temperatura do SoC",
        [data_key("cpu_temp", "CPU", "#C62828", "°C", 1, 5)],
        4, 4, 0, 20, "°C", 1, icon="memory")

    # Linha 2 - series temporais das grandezas ambientais
    add("time_series_chart", "Temperatura e umidade",
        [data_key("temperature", "Temperatura (C)", "#E65100", "°C", 1, 6, series=True),
         data_key("humidity", "Umidade (%rH)", "#0277BD", "%", 1, 7, series=True)],
        12, 6, 4, 0)
    add("time_series_chart", "Luminosidade e pressao",
        [data_key("light", "Luminosidade", "#F9A825", "", 0, 8, series=True),
         data_key("pressure", "Pressao (hPa)", "#6A1B9A", "hPa", 1, 9, series=True)],
        12, 6, 4, 12)

    # Linha 3 - evidencia da compensacao termica e qualidade do enlace
    add("time_series_chart", "Compensacao termica: bruto x compensado x SoC",
        [data_key("temperature_raw", "HTS221 bruto", "#9E9E9E", "°C", 1, 10, series=True),
         data_key("temperature", "Compensado", "#E65100", "°C", 1, 11, series=True),
         data_key("cpu_temp", "SoC", "#C62828", "°C", 1, 12, series=True)],
        12, 6, 10, 0)
    add("time_series_chart", "Qualidade do enlace Wi-Fi (RSSI)",
        [data_key("wifi_rssi", "RSSI (dBm)", "#00695C", "dBm", 0, 13, series=True)],
        12, 6, 10, 12)

    return {
        "description": "Monitoramento ambiental com Raspberry Pi 5 + Sense HAT (Android AOSP)",
        "widgets": widgets,
        "states": {"default": {
            "name": "GreenPi Monitor", "root": True,
            "layouts": {"main": {
                "widgets": layout,
                "gridSettings": {
                    "backgroundColor": "#F4F6F4", "columns": 24, "margin": 8,
                    "backgroundSizeMode": "100%", "autoFillHeight": False,
                    "mobileAutoFillHeight": False, "mobileRowHeight": 70,
                },
            }},
        }},
        "entityAliases": {alias_id: {
            "id": alias_id, "alias": DEVICE_NAME,
            "filter": {
                "type": "singleEntity", "resolveMultiple": False,
                "singleEntity": {"entityType": "DEVICE", "id": device_id},
            },
        }},
        # Janela padrao herdada pelos graficos. A estrutura reproduz exatamente o
        # formato que os widgets nativos do ThingsBoard utilizam.
        "timewindow": {
            "hideInterval": False,
            "hideLastInterval": False,
            "hideQuickInterval": False,
            "hideAggregation": False,
            "hideAggInterval": False,
            "hideTimezone": False,
            "selectedTab": 0,
            "realtime": {
                "realtimeType": 0,
                "timewindowMs": 1800000,
                "quickInterval": "CURRENT_DAY",
                "interval": 1000,
            },
            # Sem agregacao: os pontos brutos vao direto para o grafico, que
            # passa a desenhar assim que o painel abre.
            "aggregation": {"type": "NONE", "limit": 50000},
            "timezone": None,
        },
        "settings": {
            "stateControllerId": "entity", "showTitle": True,
            "showDashboardsSelect": False, "showEntitiesSelect": False,
            "showDashboardTimewindow": True, "showDashboardExport": True,
            "toolbarAlwaysOpen": True,
        },
    }


def ensure_dashboard(device_id):
    catalog = widget_catalog()
    configuration = build_configuration(device_id, catalog)

    existing = None
    page = api("/api/tenant/dashboards?pageSize=200&page=0")
    for item in page.get("data", []):
        if item["title"] == DASHBOARD_TITLE:
            existing = item
            break

    if existing:
        full = api(f"/api/dashboard/{existing['id']['id']}")
        full["configuration"] = configuration
        dashboard = api("/api/dashboard", "POST", full)
        print("[ok] dashboard atualizado")
    else:
        dashboard = api("/api/dashboard", "POST",
                        {"title": DASHBOARD_TITLE, "configuration": configuration})
        print("[ok] dashboard criado")

    dashboard_id = dashboard["id"]["id"]
    if MAKE_PUBLIC:
        api(f"/api/customer/public/device/{device_id}", "POST")
        api(f"/api/customer/public/dashboard/{dashboard_id}", "POST")
        info = api(f"/api/dashboard/info/{dashboard_id}")
        public_id = info["assignedCustomers"][0]["customerId"]["id"]
        print(f"[ok] link publico: {TB_URL}/dashboard/{dashboard_id}?publicId={public_id}")
    return dashboard_id


def main():
    login()
    device_id, token = ensure_device()
    dashboard_id = ensure_dashboard(device_id)

    print()
    print("=" * 62)
    print("Configure estes valores na tela de Configuracoes do aplicativo:")
    print(f"  Host ......................: {urllib.parse.urlparse(TB_URL).hostname}")
    print(f"  Porta MQTT ................: 1883")
    print(f"  Porta HTTP ................: {urllib.parse.urlparse(TB_URL).port or 80}")
    print(f"  Token de acesso ...........: {token}")
    print(f"  Nome do dispositivo .......: {DEVICE_NAME}")
    print()
    print(f"Dashboard web: {TB_URL}/dashboards/{dashboard_id}")
    print("=" * 62)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as e:
        print("ERRO:", e, file=sys.stderr)
        sys.exit(1)
