# ZET6 当前资料

这个文件夹现在只保留新板子 STM32F103ZET6 需要看的资料。

## 先看这个版本

当前要用的完整程序在：

```text
F:\Code-\bang-bang-agro-master\1.0\ZET6\05_扩展版_土壤传感器_风扇
```

FlyMCU 烧录这个 hex：

```text
F:\Code-\bang-bang-agro-master\1.0\ZET6\05_扩展版_土壤传感器_风扇\烧录这个_stm32f103_土壤风扇版.hex
```

Keil 打开这个工程：

```text
F:\Code-\bang-bang-agro-master\1.0\ZET6\05_扩展版_土壤传感器_风扇\最终单片机程序_土壤风扇版\stm32f103.uvprojx
```

主程序入口是这个 main.c：

```text
F:\Code-\bang-bang-agro-master\1.0\ZET6\05_扩展版_土壤传感器_风扇\最终单片机程序_土壤风扇版\user\main.c
```

注意：`main.c` 是主入口，但不是唯一代码。风扇、蜂鸣器、土壤传感器、ESP8266、OneNet 上传这些功能还依赖工程里的其他 `.c/.h` 文件，所以不要只复制一个 `main.c` 去烧录。

## 接线说明

接线说明看：

```text
F:\Code-\bang-bang-agro-master\1.0\ZET6\05_扩展版_土壤传感器_风扇\接线和烧录说明.md
```

## 当前引脚

```text
ESP-01S:
  3V3 -> 3V3
  GND -> GND
  TX  -> PA3
  RX  -> PA2
  EN  -> 3V3

土壤湿度传感器:
  VCC -> 3V3
  GND -> GND
  AO  -> PA4
  DO  -> 不接

5V 风扇模块:
  VCC  -> 5V
  GND  -> GND
  GPIO -> PE5

低电平触发蜂鸣器:
  VCC -> 3V3
  GND -> GND
  IN  -> PE6
```

## 保留的目录

```text
02_ZET6新板资料
  新 ZET6 板子的说明、原理图等资料

03_接线照片
  你发来的接线照片

05_扩展版_土壤传感器_风扇
  当前唯一要用的代码、hex、接线说明
```

旧版本代码目录已经删除，避免后面找错。
