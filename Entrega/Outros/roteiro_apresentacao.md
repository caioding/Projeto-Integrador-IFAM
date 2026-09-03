# Roteiro da apresentação — GreenPi Monitor
### Vídeo de até 15 minutos · demonstração funcional do protótipo

> A avaliação concentra 30% da nota no critério **"Estado Final do Produto"**.
> Por isso o bloco de demonstração ao vivo é o mais longo do roteiro.

---

## Antes de começar (checklist de 10 minutos)

Faça isto **antes** de apertar o botão de gravar. Se algum item falhar, você ainda
tem tempo de corrigir.

| # | Verificação | Comando ou ação |
|---|---|---|
| 1 | Plataforma no ar | `docker ps --filter name=thingsboard` — deve mostrar "Up" |
| 2 | Dashboard abre | Acesse `http://localhost:8080` e faça **login novo** (evita o erro de WebSocket por token expirado) |
| 3 | Raspberry Pi conectada | `adb devices` — deve listar o dispositivo |
| 4 | Coleta rodando | Abra o app na Pi e confirme o contador de envios subindo |
| 5 | Celular/emulador pronto | App aberto em modo painel, mostrando os mesmos valores |
| 6 | Rede | Pi e computador na **mesma rede**; confirme o IP em Configurações |
| 7 | Plano B | Deixe o vídeo pré-gravado da demo aberto em outra aba, caso a rede caia |
| 8 | Tela | Feche notificações, silencie o telefone, aumente a fonte do terminal |

---

## Bloco 1 — Abertura e contextualização · 1 min 30 s
**Tela:** slide de capa do Status Report 1.

Diga, nesta ordem:

1. **Quem e o quê** — "Caio Cesar, projeto final do curso de Android e IoT. O projeto se chama GreenPi Monitor."
2. **O problema, em uma frase** — "Quem estuda plantas não tem como acompanhar continuamente as condições do ambiente. As medições são manuais e pontuais, então as variações da madrugada, que são justamente as que afetam a planta, passam despercebidas."
3. **A solução, em uma frase** — "Uma Raspberry Pi 5 com Android lê sensores ambientais reais a cada dez segundos, publica na nuvem por MQTT, e um app mostra as condições com alerta por cor e o histórico completo."
4. **O que o avaliador vai ver** — "Nos próximos minutos eu mostro a arquitetura e depois o sistema funcionando de ponta a ponta, com dado real."

> Não leia o slide. Fale olhando para a câmera; o slide é apoio.

---

## Bloco 2 — Arquitetura do sistema · 2 min
**Tela:** slide "Arquitetura do sistema" (diagrama).

Percorra o diagrama **da esquerda para a direita**, sem se perder em detalhe:

- **Dispositivo embarcado** — "Raspberry Pi 5 rodando Android 16. O Sense HAT tem três sensores: HTS221 para umidade e temperatura, LPS25H para pressão e TCS3400 para luminosidade."
- **Camada nativa** — "O Android desta imagem não expõe HAL de sensores: o SensorManager retorna vazio. Então eu escrevi uma camada em C, compilada com o NDK, que fala direto com o barramento I2C. É o mesmo caminho de uma HAL."
- **Rede** — "A telemetria sobe por MQTT com QoS 1, a cada dez segundos."
- **Plataforma** — "ThingsBoard num container Docker: broker, banco de série temporal e dashboard, tudo em um serviço só."
- **Volta** — "O app consulta o histórico por REST com JWT. O mesmo APK roda na Pi como coletor e no celular como painel."

Feche o bloco com a frase que amarra tudo: *"Os seis pilares do curso estão nesse caminho — se eu tirar qualquer um, o sistema para."*

---

## Bloco 3 — Hardware e validação · 1 min
**Tela:** slide "Hardware e barramento I2C".

- Mostre a foto/diagrama da montagem empilhada.
- "Antes de escrever uma linha do app, validei o hardware pelo terminal."
- **Se der tempo, rode ao vivo** (fica muito bem):

```bash
adb shell sh /data/local/tmp/validar_sensores.sh
```

- Aponte na saída: "Cada chip responde ao WHO_AM_I com o valor do datasheet: 0xBC, 0xBD e 0x90."

---

## Bloco 4 — DEMONSTRAÇÃO AO VIVO · 6 min  ← **o bloco mais importante**

### 4.1 · O coletor na Raspberry Pi (2 min)
**Tela:** app rodando na Pi 5 (compartilhe a tela do display ou filme o kit).

1. Mostre a tela principal: "Modo coletor + painel, Sense HAT detectado em /dev/i2c-1."
2. Aponte os cartões e leia os valores em voz alta.
3. **Faça a temperatura mudar ao vivo** — segure a mão sobre o Sense HAT por uns 20 segundos, ou aproxime uma lâmpada. Comente enquanto muda: "Repare que o cartão está mudando de cor conforme sai da faixa ideal."
4. **Cubra o sensor de luz com a mão** — a luminosidade cai e o status vira ATENÇÃO. É o efeito visual mais imediato da demonstração.
5. Aponte o rodapé: "Conectado ao broker, com o contador de envios subindo."

### 4.2 · A plataforma recebendo (1 min 30 s)
**Tela:** dashboard do ThingsBoard no navegador.

1. "Este é o mesmo dado, já na plataforma."
2. Mostre os seis cartões de valor corrente.
3. **Vá direto ao gráfico "Compensação térmica"** — é o mais forte tecnicamente: "As três curvas mostram o problema e a solução: em vermelho o processador a 57 graus, em cinza a leitura bruta do sensor e em laranja o valor corrigido."
4. Mostre o gráfico de RSSI: "Também monitoro a qualidade do enlace Wi-Fi."

> Se o painel não atualizar sozinho, **atualize a página** e siga sem comentar.
> É uma limitação conhecida da versão 4.2.1 do ThingsBoard, registrada no relatório.

### 4.3 · O app no celular (1 min 30 s)
**Tela:** celular ou emulador.

1. "O mesmo arquivo APK, agora num celular."
2. "Como não existe /dev/i2c-1 aqui, o app detecta isso e assume só o papel de painel — repare que o botão de coleta está desabilitado."
3. Compare os números lado a lado com a Pi: "Os valores são os mesmos, vindos da plataforma por REST."

### 4.4 · O histórico (1 min)
**Tela:** tela de Histórico do app.

1. "Aqui estão as últimas três horas, consultadas por REST e desenhadas em Canvas, sem biblioteca de gráficos."
2. Aponte a subida da curva de umidade: "Dá para ver a variação real ao longo do tempo — é exatamente a informação que a medição manual perde."

---

## Bloco 5 — Desafios técnicos · 2 min
**Tela:** slide "Desafios técnicos e soluções".

Escolha **dois** e conte bem, em vez de listar sete às pressas:

**1. O Android não expõe sensores (40 s)**
> "O caminho normal seria o SensorManager, mas nesta imagem ele retorna vazio, e os drivers de kernel do HTS221 e do LPS25H nem completam o bind. Por isso a camada em C, falando direto com o barramento por ioctl."

**2. O auto-aquecimento — o mais interessante (1 min 20 s)**
> "Este é o problema que quase invalidou o projeto. O Sense HAT fica a milímetros do processador, que roda a 57 graus. O sensor lia 40 graus com o ambiente a 26. Um sistema de monitoramento ambiental que mede a temperatura da própria placa não serve para nada."
>
> "A correção subtrai uma fração da diferença entre a temperatura da CPU e a do sensor, com um fator k que deixei ajustável na tela de configurações, porque depende da montagem. E eu continuo publicando o valor bruto, para que a compensação seja auditável."

---

## Bloco 6 — Resultados e conclusão · 1 min 30 s
**Tela:** slide "Resultados já alcançados".

1. **Números** — "Três sensores lidos por I2C, oito grandezas publicadas a cada dez segundos, mais de 800 pontos coletados nos testes, sem perda."
2. **Integração** — "Os seis pilares do curso estão integrados num caminho único de dados."
3. **Honestidade sobre limites** — "A luminosidade é publicada como índice, não em lux; uma conversão calibrada exigiria fonte de luz de referência. E o fator de compensação é empírico, precisa ser reajustado se a montagem mudar."
4. **Aprendizado** — "O que mais aprendi veio do problema que não estava previsto. Diagnosticar o auto-aquecimento e deixar a correção auditável foi o que transformou uma leitura de sensor em uma medição confiável."
5. **Fechamento** — "Obrigado. O código, os scripts e a documentação estão na entrega."

---

## Margem · 1 min
Sobra para imprevistos: demora de carregamento, uma pergunta, ou repetir a
demonstração do sensor de luz.

---

## Distribuição do tempo

| Bloco | Duração | Acumulado |
|---|---|---|
| 1. Contextualização | 1 min 30 s | 1:30 |
| 2. Arquitetura | 2 min | 3:30 |
| 3. Hardware e validação | 1 min | 4:30 |
| **4. Demonstração ao vivo** | **6 min** | **10:30** |
| 5. Desafios técnicos | 2 min | 12:30 |
| 6. Resultados e conclusão | 1 min 30 s | 14:00 |
| Margem | 1 min | 15:00 |

---

## Erros que custam nota

- **Ler os slides.** O avaliador também sabe ler.
- **Gastar 8 minutos em arquitetura e 2 na demo.** O peso está na demonstração.
- **Esconder a limitação do painel web.** Se ele não atualizar, atualize a página e siga. Se perguntarem, explique — está documentado no relatório.
- **Deixar a Pi em rede diferente do computador.** Confirme antes de gravar.
- **Não fazer nada mudar na tela.** Sem a mão sobre o sensor, a demo vira uma foto parada.

## Se algo der errado durante a gravação

| Problema | O que fazer |
|---|---|
| App não conecta ao broker | Verifique o IP em Configurações (não pode ser `localhost`) e use "Testar conexão MQTT" |
| Dashboard sem dados | Atualize a página; confirme que a coleta está ativa na Pi |
| Erro de WebSocket no navegador | Faça logout e login novamente no ThingsBoard |
| adb perdeu a Pi | `adb kill-server && adb start-server`, depois reconecte pelo Wireless Debugging |
| Nada funciona | Use o vídeo pré-gravado — por isso ele fica aberto numa aba desde o início |
