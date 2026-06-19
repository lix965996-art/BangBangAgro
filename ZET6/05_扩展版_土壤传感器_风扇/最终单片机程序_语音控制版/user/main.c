#include "stm32f10x.h"

#include "onenet.h"
#include "esp8266.h"

#include "delay.h"
#include "usart.h"
#include "led.h"
#include "key.h"
#include "dht11.h"
#include "oled.h"
#include "soil.h"
#include "fan.h"
#include "buzzer.h"

#include <stdio.h>
#include <string.h>

#define ESP8266_ONENET_INFO "AT+CIPSTART=\"TCP\",\"mqtts.heclouds.com\",1883\r\n"

#define TEMP_FAN_ON_C       30
#define TEMP_FAN_OFF_C      28
#define TEMP_ALARM_ON_C     35
#define TEMP_ALARM_OFF_C    32
#define SOIL_ALARM_ON_PERCENT  25
#define SOIL_ALARM_OFF_PERCENT 35

void Hardware_Init(void);
void Display_Init(void);
void Refresh_Data(void);
void Update_Soil_And_Control(void);
void Process_Voice_Command(void);

u8 temp, humi;
u16 soil_raw;
u8 soil_percent;
u8 voice_manual_mode = 0;

int main(void)
{
	unsigned short timeCount = 0;
	unsigned char *dataPtr = NULL;
	
	Hardware_Init();
	ESP8266_Init();

	OLED_Clear();
	OLED_ShowString(0, 0, (u8 *)"Connect MQTT...", 16);
	while(ESP8266_SendCmd(ESP8266_ONENET_INFO, "CONNECT"))
		DelayXms(500);
	
	OLED_ShowString(0, 4, (u8 *)"MQTT OK", 16);
	DelayXms(500);

	OLED_Clear();
	OLED_ShowString(0, 0, (u8 *)"Device login ...", 16);
	while(OneNet_DevLink())
	{
		ESP8266_SendCmd(ESP8266_ONENET_INFO, "CONNECT");
		DelayXms(500);
	}

	OneNET_Subscribe();
	Display_Init();
	
	while(1)
	{
		Process_Voice_Command();

		if(++timeCount >= 100)
		{
			DHT11_Read_Data(&temp, &humi);
			Update_Soil_And_Control();
			OneNet_SendData();
			
			timeCount = 0;
			ESP8266_Clear();
		}
		
		dataPtr = ESP8266_GetIPD(0);
		if(dataPtr != NULL)
			OneNet_RevPro(dataPtr);
		
		Refresh_Data();
		DelayMs(10);
	}
}

void Hardware_Init(void)
{
	NVIC_PriorityGroupConfig(NVIC_PriorityGroup_2);

	Delay_Init();
	Usart1_Init(115200);
	Usart2_Init(115200);
	Usart3_Init(115200);
	
	Key_Init();
	Led_Init();
	Fan_Init();
	Buzzer_Init();
	Soil_Init();
	OLED_Init();
	
	while(DHT11_Init())
	{
		OLED_ShowString(0, 0, (u8 *)"DHT11 Error", 16);
		DelayMs(1000);
	}
	
	DHT11_Read_Data(&temp, &humi);
	Update_Soil_And_Control();
	
	OLED_Clear();
	OLED_ShowString(0, 0, (u8 *)"Hardware init OK", 16);
	DelayMs(1000);
}

void Update_Soil_And_Control(void)
{
	soil_raw = Soil_ReadRaw();
	soil_percent = Soil_ReadPercent();
	
	if(!voice_manual_mode)
	{
		if(temp >= TEMP_FAN_ON_C)
			Fan_Set(FAN_ON);
		else if(temp <= TEMP_FAN_OFF_C)
			Fan_Set(FAN_OFF);
	}
	
	if(temp >= TEMP_ALARM_ON_C || soil_percent <= SOIL_ALARM_ON_PERCENT)
		Buzzer_Set(BUZZER_ON);
	else if(temp <= TEMP_ALARM_OFF_C && soil_percent >= SOIL_ALARM_OFF_PERCENT)
		Buzzer_Set(BUZZER_OFF);
}

void Process_Voice_Command(void)
{
	u8 command = Voice_GetCommand();

	switch(command)
	{
		case 0x04:
			voice_manual_mode = 1;
			Fan_Set(FAN_ON);
			break;

		case 0x05:
			voice_manual_mode = 1;
			Fan_Set(FAN_OFF);
			break;

		case 0x06:
			Led_Set(LED_ON);
			break;

		case 0x07:
		case 0x0A:
			Led_Set(LED_OFF);
			break;

		case 0x03:
			voice_manual_mode = 0;
			break;

		default:
			break;
	}
}

void Display_Init(void)
{
	OLED_Clear();
	OLED_ShowString(0, 0, (u8 *)"Temp:", 16);
	OLED_ShowString(0, 2, (u8 *)"Humi:", 16);
	OLED_ShowString(0, 4, (u8 *)"Soil:", 16);
	OLED_ShowString(0, 6, (u8 *)"F:", 16);
	OLED_ShowString(56, 6, (u8 *)"B:", 16);
}

void Refresh_Data(void)
{
	char buf[16];
	
	sprintf(buf, "%2dC ", temp);
	OLED_ShowString(48, 0, (u8 *)buf, 16);
	
	sprintf(buf, "%2d%% ", humi);
	OLED_ShowString(48, 2, (u8 *)buf, 16);
	
	sprintf(buf, "%3d%% ", soil_percent);
	OLED_ShowString(48, 4, (u8 *)buf, 16);
	
	if(fan_info.Fan_Status)
		OLED_ShowString(24, 6, (u8 *)"ON ", 16);
	else
		OLED_ShowString(24, 6, (u8 *)"OFF", 16);
	
	if(buzzer_info.Buzzer_Status)
		OLED_ShowString(80, 6, (u8 *)"ON ", 16);
	else
		OLED_ShowString(80, 6, (u8 *)"OFF", 16);
}
