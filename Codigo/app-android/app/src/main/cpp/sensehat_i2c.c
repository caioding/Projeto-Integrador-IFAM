/*
 * sensehat_i2c.c - Camada nativa de acesso ao barramento I2C do Raspberry Pi 5.
 *
 * O Android (AOSP) instalado na Raspberry Pi 5 nao expoe uma HAL de sensores:
 * "dumpsys sensorservice" retorna "No Sensors on the device". Alem disso, os
 * drivers de kernel do HTS221 e do LPS25H nao completam o bind (ficam em
 * "waiting_for_supplier"), de modo que o subsistema IIO tambem nao esta
 * disponivel para esses chips.
 *
 * A solucao adotada e falar diretamente com o barramento pelo dispositivo de
 * caractere /dev/i2c-1, que na imagem utilizada tem permissao crw-rw-rw-,
 * dispensando privilegios de root. Cada transacao usa o ioctl I2C_RDWR com
 * duas mensagens (escrita do registrador + leitura dos dados), o que gera um
 * "repeated start" - a sequencia exigida pelos sensores da ST.
 *
 * Curso de Android e Internet das Coisas (IoT) - Instituto de Pesquisas Eldorado
 */

#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <linux/i2c.h>
#include <linux/i2c-dev.h>
#include <android/log.h>

#define TAG "SenseHatNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* Abre o dispositivo do barramento. Retorna o descritor ou -1 em caso de erro. */
JNIEXPORT jint JNICALL
Java_com_greenpi_monitor_SenseHat_nativeOpen(JNIEnv *env, jobject thiz, jstring path) {
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    int fd = open(cpath, O_RDWR);
    if (fd < 0) {
        LOGE("open(%s) falhou: %s", cpath, strerror(errno));
    }
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    return (jint) fd;
}

JNIEXPORT void JNICALL
Java_com_greenpi_monitor_SenseHat_nativeClose(JNIEnv *env, jobject thiz, jint fd) {
    if (fd >= 0) close(fd);
}

/*
 * Le "len" bytes a partir de "reg" no dispositivo "addr".
 * Retorna um byte[] com os dados lidos, ou null se a transacao falhar.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_greenpi_monitor_SenseHat_nativeReadBlock(JNIEnv *env, jobject thiz,
                                                  jint fd, jint addr, jint reg, jint len) {
    if (fd < 0 || len <= 0 || len > 64) return NULL;

    unsigned char regbuf[1];
    unsigned char data[64];
    regbuf[0] = (unsigned char) (reg & 0xFF);

    struct i2c_msg msgs[2];
    msgs[0].addr = (unsigned short) addr;
    msgs[0].flags = 0;              /* escrita do endereco do registrador */
    msgs[0].len = 1;
    msgs[0].buf = regbuf;

    msgs[1].addr = (unsigned short) addr;
    msgs[1].flags = I2C_M_RD;       /* repeated start + leitura */
    msgs[1].len = (unsigned short) len;
    msgs[1].buf = data;

    struct i2c_rdwr_ioctl_data xfer;
    xfer.msgs = msgs;
    xfer.nmsgs = 2;

    if (ioctl(fd, I2C_RDWR, &xfer) < 0) {
        LOGE("leitura addr=0x%02x reg=0x%02x falhou: %s", addr, reg, strerror(errno));
        return NULL;
    }

    jbyteArray out = (*env)->NewByteArray(env, len);
    if (out == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte *) data);
    return out;
}

/* Escreve um byte em um registrador. Retorna 0 em caso de sucesso. */
JNIEXPORT jint JNICALL
Java_com_greenpi_monitor_SenseHat_nativeWriteReg(JNIEnv *env, jobject thiz,
                                                 jint fd, jint addr, jint reg, jint value) {
    if (fd < 0) return -1;

    unsigned char buf[2];
    buf[0] = (unsigned char) (reg & 0xFF);
    buf[1] = (unsigned char) (value & 0xFF);

    struct i2c_msg msg;
    msg.addr = (unsigned short) addr;
    msg.flags = 0;
    msg.len = 2;
    msg.buf = buf;

    struct i2c_rdwr_ioctl_data xfer;
    xfer.msgs = &msg;
    xfer.nmsgs = 1;

    if (ioctl(fd, I2C_RDWR, &xfer) < 0) {
        LOGE("escrita addr=0x%02x reg=0x%02x falhou: %s", addr, reg, strerror(errno));
        return -1;
    }
    return 0;
}
