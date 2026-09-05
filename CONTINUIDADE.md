# Continuidade

Estado do projeto em 5 de setembro de 2026.

## Onde esta o projeto

O sistema funciona de ponta a ponta e foi verificado em hardware real: a
Raspberry Pi le os sensores, publica por MQTT a cada 10 s, o ThingsBoard grava e
o app exibe. Repositorio publicado em
https://github.com/caioding/Projeto-Integrador-IFAM (branch `main`).

## O que foi feito nesta sessao

**Publicacao do repositorio.** A estrutura foi reorganizada — `Entrega/Codigo` e
`Entrega/Outros` subiram para a raiz como `Codigo/` e `Outros/`, e a pasta `Docs`
(material de apoio da disciplina) saiu do versionamento. O README foi atualizado
para os caminhos novos.

**Correcao do modo de falha silencioso.** Era o problema central da sessao. Com a
Raspberry Pi desligada, o app e o dashboard continuavam exibindo a ultima leitura
gravada como se fosse a condicao atual, sem nenhum aviso — falha que nao parece
falha. Tres commits:

- `bb96044` — no app: `latestValues` descartava o campo `ts` da resposta do
  ThingsBoard, e a amostra remota era carimbada com o relogio local no momento da
  consulta. Passou a devolver `TsValue(ts, valor)`; amostra vencida vira
  `SEM CONTATO`, com valores esmaecidos e a idade da ultima leitura. Um ticker de
  5 s redesenha a tela sem depender de dado novo — sem ele a interface congelaria
  justamente quando a Pi para.
- `b533763` / `85c285e` — no dashboard: faixa no topo lendo o atributo `active`.
  A primeira versao tinha um bug de truthiness (`"false"` e verdadeiro em JS) e
  exibia ONLINE com a Pi desligada; corrigido com normalizacao explicita e cores
  verde/vermelho.
- `8b4615b` — a faixa herdava a fonte de 28px dos cartoes pequenos e ficava
  ilegivel; agora tem tipografia propria.

**Verificacao em hardware.** O ciclo completo foi testado na Pi por adb: iniciar
coleta, publicar por 40 s, parar, e confirmar que a tela vira `SEM CONTATO` com o
contador subindo. A captura esta descrita no historico da conversa.

## Prazos de deteccao

O app acusa aos **30 s** e o dashboard aos **60 s**. E intencional e esta
documentado no README, na secao "Deteccao de perda de contato": o app e o painel
que se olha de perto e deve reagir rapido; a plataforma e o registro historico e
tolera mais. Decisao tomada conscientemente — nao "consertar" sem motivo.

## Configuracao atual do ambiente

- ThingsBoard em Docker neste Mac. **O IP e por DHCP e ja mudou uma vez** (de
  `192.168.0.8` para `192.168.0.4`), quebrando o app nos dois papeis. Antes da
  apresentacao, reserve um IP fixo no roteador — se cair no meio da demo, e chato
  de diagnosticar na hora.
- O APK atual esta instalado na Raspberry Pi e no emulador.
- **O emulador ainda nao foi configurado**: falta preencher host, token e senha do
  tenant na tela de Configuracoes dele. Sem isso mostra "Falha ao consultar a
  plataforma". A configuracao e por aparelho.
- Ha um container `backend_helpaus` em restart loop no Docker. Nao tem relacao com
  este projeto, mas consome recursos.

## Pendencias

**Documentos da disciplina.** O README antigo citava um relatorio final (`.docx`)
e dois Status Reports (`.pptx`) que nao estao no repositorio. Para um Projeto
Integrador, provavelmente deveriam estar.

**Pastas vazias.** `Outros/anotacoes` e `Outros/referencias` existem em disco mas
estao vazias, entao o Git nao as versiona — nao aparecem no GitHub. Ou falta
conteudo, ou podem ser removidas.

**Repositorio antigo.** `caioding/greenpi-monitor` continua no GitHub,
desconectado deste projeto. Arquivar ou apagar e decisao do autor, pelas Settings
do repositorio.

## Sugestoes para a apresentacao

O modo de falha silencioso e um bom material de defesa: e um problema real de
sistemas de telemetria, nao aparece em teste de bancada, e a correcao envolveu as
duas pontas por motivos diferentes — no app faltava informacao (o timestamp era
descartado), no dashboard faltava uma armadilha de linguagem ser evitada. Da uma
narrativa melhor que so mostrar os graficos funcionando.

Vale ensaiar a demonstracao de desligar a Pi: o celular acusa em 30 s e o
dashboard em 60 s, e explicar por que os prazos diferem mostra que a escolha foi
deliberada.
