#ifndef HELLOJNI_H
#define HELLOJNI_H

#include <string.h>

#define BUFFER_OFFSET(i) ((char*)NULL + (i))
#define UNUSED(i) ((char*)NULL + (i))

struct TEAPOT_VERTEX {
    float pos[3];
    float normal[3];
};

#endif //HELLOJNI_H
