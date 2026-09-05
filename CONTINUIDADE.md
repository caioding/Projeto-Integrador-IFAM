# Continuidade

Estado do projeto em 5 de setembro de 2026.

## Onde esta o projeto

Sistema funcionando de ponta a ponta, verificado em hardware real: a Raspberry Pi
le os sensores, publica por MQTT a cada 10 s, o ThingsBoard grava e o app exibe.
Publicado em https://github.com/caioding/Projeto-Integrador-IFAM (branch `main`).

## O que foi feito nesta sessao

**Publicacao do repositorio.** `Entrega/Codigo` e `Entrega/Outros` subiram para a
raiz como `Codigo/` e `Outros/`; a pasta `Docs` saiu do versionamento. README
atualizado para os caminhos novos.

**Correcao do modo de falha silencioso** (`bb96044`, `b533763`, `85c285e`,
`8b4615b`). Com a Pi desligada, app e dashboard seguiam exibindo a ultima leitura
como se fosse a condicao atual. No app, `latestValues` descartava o campo `ts` da
resposta e a amostra remota era carimbada com o relogio local; passou a devolver
`TsValue(ts, valor)`, e amostra vencida vira `SEM CONTATO` com os valores
esmaecidos. No dashboard, faixa no topo lendo o atributo `active` — a primeira
versao exibia ONLINE com a Pi desligada por um bug de truthiness (`"false"` e
verdadeiro em JS).

**Descoberta da contaminacao por LED** (`0494945`). A matriz de LED do Sense HAT
acende no boot e fica acesa; esta a centimetros do sensor de luz. Medido: **545
contagens acesa contra 79 apagada**, ou seja, 85% da "luminosidade" vinha da
propria placa. Consequencia mais grave que o desvio: como o LED injetava ~466
contagens, o valor nunca descia do limiar de 100 e o alerta de pouca luz nao
podia disparar nem no escuro total. Limiares recalibrados para `>60` / `>=20`, e
o passo `sensehat_cli clear` documentado no README.

**Verificacao de Wi-Fi e CPU.** Ambos corretos. `thermal_zone0` e do tipo
`cpu-thermal` e e a unica zona da placa; o kernel devolveu 50700 milesimos e o
app publicou 50,2 C. O framework reportou RSSI −53 dBm e o app publicou −53.

## Estado do ambiente

- ThingsBoard em Docker no MacBook. IPs por DHCP: Mac em `192.168.0.4`, Pi em
  `192.168.0.10` no momento desta anotacao. **Ja mudou uma vez e quebrou o app.**
- APK atual instalado na Raspberry Pi e no emulador.
- **O emulador ainda nao foi configurado** — faltam host, token e senha do tenant
  na tela de Configuracoes dele. Use host `10.0.2.2`, que sempre alcanca o Mac.
- **A matriz de LED esta apagada agora**, mas reacende no proximo boot.
- Container `backend_helpaus` em restart loop no Docker, sem relacao com o projeto.

## Pendencias

**Recoletar os dados de luminosidade.** Todo o historico anterior a hoje esta
inflado pela matriz de LED e nao representa o ambiente. Os graficos das
evidencias precisam ser refeitos antes de fechar o relatorio.

**Refinar os limiares de luz.** Os valores `60` / `20` vieram de um unico ponto
de referencia (sala iluminada de dia, matriz apagada, ~79 contagens). Uma
medicao no escuro e outra com luz forte fechariam a calibracao. Comparar com um
luximetro seria o ideal.

**Testar a demonstracao no hotspot do celular** antes de sair de casa. E a
defesa contra Wi-Fi com isolamento de clientes, que quebraria a demo sem que
nenhuma configuracao do app resolvesse.

**Documentos da disciplina.** O relatorio final (`.docx`) e os dois Status
Reports (`.pptx`) nao estao no repositorio.

**Pastas vazias.** `Outros/anotacoes` e `Outros/referencias` existem em disco mas
estao vazias, entao o Git nao as versiona.

**Repositorio antigo.** `caioding/greenpi-monitor` continua no GitHub,
desconectado deste projeto.

**Codigo morto.** `SystemStats.wifiSsid` nunca e chamado.

## Ideias nao implementadas

**App apagando a matriz ao iniciar a coleta.** Seria a correcao de engenharia
certa — o sistema garantindo a validade da propria medicao em vez de depender de
um passo manual. Exige escrever no `0x46` (192 bytes de RGB a zerar), um chip que
o app hoje nao toca. Adiado por ser codigo novo em camada I2C perto da entrega.

**Publicar os canais R, G e B.** Ja sao lidos do barramento e descartados.
Renderia um grafico de composicao espectral com custo quase zero.

**Descoberta automatica do servidor na rede.** Eliminaria a troca manual de IP a
cada local, mas e codigo novo numa camada que funciona.

## Para a apresentacao

Duas historias boas, ambas do mesmo tipo: o sistema medindo a si proprio em vez
do ambiente.

A **compensacao termica** ja era o destaque planejado — e ganhou uma confirmacao
independente, porque o `sensehat_cli temp` do fabricante reporta ~35,7 C crus
enquanto o app mostra ~26 C compensados no mesmo ambiente. Vale colocar esse
print ao lado do grafico "bruto x compensado x SoC".

A **contaminacao por LED** e a mesma classe de problema na luminosidade,
descoberta depois: um sensor lendo a propria placa. Rende ainda a discussao sobre
uma funcionalidade que estava morta e parecia viva.

O **modo de falha silencioso** completa o conjunto: e um problema real de
telemetria, nao aparece em teste de bancada, e a correcao precisou de coisas
diferentes nas duas pontas.

Vale ensaiar a demonstracao de desligar a Pi: o celular acusa em 30 s e o
dashboard em 60 s. Os prazos diferem de proposito, e explicar por que mostra que
a escolha foi deliberada — esta documentado no README.
