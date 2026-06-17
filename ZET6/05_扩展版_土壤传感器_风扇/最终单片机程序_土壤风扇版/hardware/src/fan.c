#include "fan.h"

FAN_INFO fan_info = {0};

void Fan_Init(void)
{
	GPIO_InitTypeDef gpio_initstruct;
	
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOE, ENABLE);
	
	gpio_initstruct.GPIO_Pin = GPIO_Pin_5;
	gpio_initstruct.GPIO_Mode = GPIO_Mode_Out_PP;
	gpio_initstruct.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOE, &gpio_initstruct);
	
	Fan_Set(FAN_OFF);
}

void Fan_Set(_Bool status)
{
	GPIO_WriteBit(GPIOE, GPIO_Pin_5, status == FAN_ON ? Bit_SET : Bit_RESET);
	fan_info.Fan_Status = status;
}
