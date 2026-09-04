#!/system/bin/sh
#
# validar_sensores.sh - Verificacao de baixo nivel dos sensores do Sense HAT.
#
# Executa, na propria Raspberry Pi 5, a mesma sequencia de bring-up usada
# durante o desenvolvimento: varre o barramento I2C, confere a identidade de
# cada chip pelo registrador WHO_AM_I e le os dados brutos. Serve para isolar
# uma falha de hardware de um problema no aplicativo.
#
# Uso (a partir do computador de desenvolvimento):
#   adb push validar_sensores.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/validar_sensores.sh
#
# Curso de Android e Internet das Coisas (IoT) - Instituto de Pesquisas Eldorado

BUS=1

echo "=============================================="
echo " Sense HAT - validacao pelo barramento I2C-$BUS"
echo "=============================================="
echo

echo "[1/4] Dispositivos presentes no barramento"
i2cdetect -y $BUS
echo
echo "  Legenda: um valor em hexadecimal indica dispositivo livre para acesso"
echo "           direto; 'UU' indica endereco ja reservado por um driver do"
echo "           kernel; '--' indica que nada respondeu."
echo

echo "[2/4] Identificacao dos chips (registrador WHO_AM_I)"
printf "  HTS221  (0x5F) esperado 0xbc -> lido "; i2cget -y $BUS 0x5f 0x0f
printf "  LPS25H  (0x5C) esperado 0xbd -> lido "; i2cget -y $BUS 0x5c 0x0f
printf "  LSM9DS1 (0x6A) esperado 0x68 -> lido "; i2cget -y $BUS 0x6a 0x0f
printf "  TCS3400 (0x39) esperado 0x90 -> lido "; i2cget -y $BUS 0x39 0x92
echo

echo "[3/4] Ligando os sensores"
# HTS221:  CTRL_REG1 = 0x85 -> ligado, BDU habilitado, taxa de 1 Hz
i2cset -y $BUS 0x5f 0x20 0x85 b 2>/dev/null
# LPS25H:  CTRL_REG1 = 0x94 -> ligado, BDU habilitado, taxa de 1 Hz
i2cset -y $BUS 0x5c 0x20 0x94 b 2>/dev/null
# TCS3400: ATIME, ganho de 16x e ENABLE (PON + AEN)
i2cset -y $BUS 0x39 0x81 0xc0 b 2>/dev/null
i2cset -y $BUS 0x39 0x8f 0x02 b 2>/dev/null
i2cset -y $BUS 0x39 0x80 0x03 b 2>/dev/null
sleep 1
echo "  ok"
echo

echo "[4/4] Leitura dos registradores de dados"
echo "  HTS221 calibracao (0x30..0x3F):"
printf "    "; i2ctransfer -y $BUS w1@0x5f 0xb0 r16 2>/dev/null | tail -1
echo "  HTS221 dados      (umidade e temperatura, 0x28..0x2B):"
printf "    "; i2ctransfer -y $BUS w1@0x5f 0xa8 r4 2>/dev/null | tail -1
echo "  LPS25H dados      (pressao e temperatura, 0x28..0x2C):"
printf "    "; i2ctransfer -y $BUS w1@0x5c 0xa8 r5 2>/dev/null | tail -1
echo "  TCS3400 dados     (clear, red, green, blue, 0x14..0x1B):"
printf "    "; i2ctransfer -y $BUS w1@0x39 0x94 r8 2>/dev/null | tail -1
echo
echo "  Temperatura do SoC (/sys/class/thermal/thermal_zone0/temp):"
printf "    "; cat /sys/class/thermal/thermal_zone0/temp
echo
echo "Concluido. Os valores acima sao bytes brutos; a conversao para unidades"
echo "de engenharia e feita em SenseHat.kt, no aplicativo Android."
