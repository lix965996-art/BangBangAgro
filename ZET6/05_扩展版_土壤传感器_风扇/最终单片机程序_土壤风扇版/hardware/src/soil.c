#include "soil.h"

void Soil_Init(void)
{
	GPIO_InitTypeDef gpio_initstruct;
	ADC_InitTypeDef adc_initstruct;
	
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOA | RCC_APB2Periph_ADC1, ENABLE);
	RCC_ADCCLKConfig(RCC_PCLK2_Div6);
	
	gpio_initstruct.GPIO_Pin = GPIO_Pin_4;
	gpio_initstruct.GPIO_Mode = GPIO_Mode_AIN;
	gpio_initstruct.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOA, &gpio_initstruct);
	
	ADC_DeInit(ADC1);
	adc_initstruct.ADC_Mode = ADC_Mode_Independent;
	adc_initstruct.ADC_ScanConvMode = DISABLE;
	adc_initstruct.ADC_ContinuousConvMode = DISABLE;
	adc_initstruct.ADC_ExternalTrigConv = ADC_ExternalTrigConv_None;
	adc_initstruct.ADC_DataAlign = ADC_DataAlign_Right;
	adc_initstruct.ADC_NbrOfChannel = 1;
	ADC_Init(ADC1, &adc_initstruct);
	
	ADC_Cmd(ADC1, ENABLE);
	ADC_ResetCalibration(ADC1);
	while(ADC_GetResetCalibrationStatus(ADC1));
	ADC_StartCalibration(ADC1);
	while(ADC_GetCalibrationStatus(ADC1));
}

u16 Soil_ReadRaw(void)
{
	ADC_RegularChannelConfig(ADC1, ADC_Channel_4, 1, ADC_SampleTime_239Cycles5);
	ADC_SoftwareStartConvCmd(ADC1, ENABLE);
	while(ADC_GetFlagStatus(ADC1, ADC_FLAG_EOC) == RESET);
	return ADC_GetConversionValue(ADC1);
}

u8 Soil_ReadPercent(void)
{
	u16 raw = Soil_ReadRaw();
	u32 percent;
	
	if(raw <= SOIL_WET_RAW)
		return 100;
	
	if(raw >= SOIL_DRY_RAW)
		return 0;
	
	percent = (u32)(SOIL_DRY_RAW - raw) * 100 / (SOIL_DRY_RAW - SOIL_WET_RAW);
	return (u8)percent;
}
