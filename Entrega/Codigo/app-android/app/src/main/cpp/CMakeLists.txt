cmake_minimum_required(VERSION 3.22.1)
project("sensehat")

# Biblioteca nativa responsavel pelo acesso de baixo nivel ao barramento I2C.
add_library(sensehat SHARED sensehat_i2c.c)

find_library(log-lib log)
target_link_libraries(sensehat ${log-lib})
