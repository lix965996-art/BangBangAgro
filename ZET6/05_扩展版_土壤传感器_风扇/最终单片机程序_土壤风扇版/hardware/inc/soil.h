#ifndef _SOIL_H_
#define _SOIL_H_

#include "stm32f10x.h"

#define SOIL_WET_RAW  1200
#define SOIL_DRY_RAW  3500

void Soil_Init(void);
u16 Soil_ReadRaw(void);
u8 Soil_ReadPercent(void);

#endif
