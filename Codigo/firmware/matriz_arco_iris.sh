#!/system/bin/sh
# Restaura o padrao arco-iris que a matriz de LED do Sense HAT exibe quando a
# placa e energizada.
#
# O padrao original so volta cortando a alimentacao: reiniciar o Android nao o
# repoe, porque quem o escreve e o firmware do ATtiny88 ao ligar, e o Android
# nunca reescreve a matriz. Este script dispensa o desligamento.
#
# Os valores saem do proprio framebuffer do ATtiny, lido por I2C no endereco
# 0x46 com o padrao original na tela. Sao 192 bytes em tres planos de 64
# (vermelho 0-63, verde 64-127, azul 128-191), indexados por y*8+x, com 5 bits
# por canal. A conversao para a escala 0-255 do sensehat_cli compensa o
# deslocamento "valor >> 3" que ele aplica internamente.
#
# ATENCAO: a matriz acesa contamina o sensor de luminosidade - medido neste kit,
# ate ~915 contagens com todos os pixels em branco. Rode "sensehat_cli clear"
# antes de coletar dados que va usar no relatorio.
#
# Uso, na Raspberry Pi:  sh matriz_arco_iris.sh

sensehat_cli setpixel 0 0 255 0 255
sensehat_cli setpixel 1 0 255 0 255
sensehat_cli setpixel 2 0 255 0 255
sensehat_cli setpixel 3 0 255 0 223
sensehat_cli setpixel 4 0 167 0 63
sensehat_cli setpixel 5 0 31 79 0
sensehat_cli setpixel 6 0 0 255 0
sensehat_cli setpixel 7 0 0 255 0
sensehat_cli setpixel 0 1 0 255 0
sensehat_cli setpixel 1 1 0 127 15
sensehat_cli setpixel 2 1 31 23 103
sensehat_cli setpixel 3 1 151 0 255
sensehat_cli setpixel 4 1 255 0 255
sensehat_cli setpixel 5 1 255 0 255
sensehat_cli setpixel 6 1 255 0 255
sensehat_cli setpixel 7 1 255 0 255
sensehat_cli setpixel 0 2 0 191 0
sensehat_cli setpixel 1 2 0 255 0
sensehat_cli setpixel 2 2 0 255 0
sensehat_cli setpixel 3 2 0 255 0
sensehat_cli setpixel 4 2 0 255 0
sensehat_cli setpixel 5 2 0 255 39
sensehat_cli setpixel 6 2 0 87 175
sensehat_cli setpixel 7 2 63 0 255
sensehat_cli setpixel 0 3 255 0 255
sensehat_cli setpixel 1 3 255 0 255
sensehat_cli setpixel 2 3 255 0 207
sensehat_cli setpixel 3 3 151 0 55
sensehat_cli setpixel 4 3 31 87 0
sensehat_cli setpixel 5 3 0 255 0
sensehat_cli setpixel 6 3 0 255 0
sensehat_cli setpixel 7 3 0 255 0
sensehat_cli setpixel 0 4 0 119 23
sensehat_cli setpixel 1 4 39 15 119
sensehat_cli setpixel 2 4 167 0 255
sensehat_cli setpixel 3 4 255 0 255
sensehat_cli setpixel 4 4 255 0 255
sensehat_cli setpixel 5 4 255 0 255
sensehat_cli setpixel 6 4 255 0 255
sensehat_cli setpixel 7 4 255 31 151
sensehat_cli setpixel 0 5 0 255 0
sensehat_cli setpixel 1 5 0 255 0
sensehat_cli setpixel 2 5 0 255 0
sensehat_cli setpixel 3 5 0 255 0
sensehat_cli setpixel 4 5 0 239 47
sensehat_cli setpixel 5 5 0 71 191
sensehat_cli setpixel 6 5 71 0 255
sensehat_cli setpixel 7 5 239 0 255
sensehat_cli setpixel 0 6 255 0 255
sensehat_cli setpixel 1 6 255 0 191
sensehat_cli setpixel 2 6 143 15 47
sensehat_cli setpixel 3 6 23 95 0
sensehat_cli setpixel 4 6 0 255 0
sensehat_cli setpixel 5 6 0 255 0
sensehat_cli setpixel 6 6 0 255 0
sensehat_cli setpixel 7 6 0 255 0
sensehat_cli setpixel 0 7 47 15 127
sensehat_cli setpixel 1 7 175 0 255
sensehat_cli setpixel 2 7 255 0 255
sensehat_cli setpixel 3 7 255 0 255
sensehat_cli setpixel 4 7 255 0 255
sensehat_cli setpixel 5 7 255 0 255
sensehat_cli setpixel 6 7 255 31 127
sensehat_cli setpixel 7 7 95 167 23
