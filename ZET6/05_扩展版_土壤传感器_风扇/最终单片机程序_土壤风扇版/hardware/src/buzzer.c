#include "buzzer.h"

BUZZER_INFO buzzer_info = {0};

void Buzzer_Init(void)
{
	GPIO_InitTypeDef gpio_initstruct;
	
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOE, ENABLE);
	
	gpio_initstruct.GPIO_Pin = GPIO_Pin_6;
	gpio_initstruct.GPIO_Mode = GPIO_Mode_Out_PP;
	gpio_initstruct.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOE, &gpio_initstruct);
	
	Buzzer_Set(BUZZER_OFF);
}

void Buzzer_Set(_Bool status)
{
	GPIO_WriteBit(GPIOE, GPIO_Pin_6, status == BUZZER_ON ? Bit_RESET : Bit_SET);
	buzzer_info.Buzzer_Status = status;
}
