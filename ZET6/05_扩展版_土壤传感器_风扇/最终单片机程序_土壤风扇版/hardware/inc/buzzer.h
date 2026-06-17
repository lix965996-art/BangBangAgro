#ifndef _BUZZER_H_
#define _BUZZER_H_

#include "stm32f10x.h"

#define BUZZER_ON  1
#define BUZZER_OFF 0

typedef struct
{
	_Bool Buzzer_Status;
} BUZZER_INFO;

extern BUZZER_INFO buzzer_info;

void Buzzer_Init(void);
void Buzzer_Set(_Bool status);

#endif
