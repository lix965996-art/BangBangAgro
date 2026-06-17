#ifndef _FAN_H_
#define _FAN_H_

#include "stm32f10x.h"

#define FAN_ON  1
#define FAN_OFF 0

typedef struct
{
	_Bool Fan_Status;
} FAN_INFO;

extern FAN_INFO fan_info;

void Fan_Init(void);
void Fan_Set(_Bool status);

#endif
