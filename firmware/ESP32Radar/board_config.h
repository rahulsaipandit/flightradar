#ifndef BOARD_CONFIG_H
#define BOARD_CONFIG_H

// Select exactly one target board/display before compiling.
#define BOARD_V8_LANDSCAPE      // code V8 / codeforTheDutch lineage: 240x240 square TFT
// #define BOARD_MONKEY_PORTRAIT   // codeForTheMonkey / codefortheCanadianMonkey lineage: 320x480 portrait TFT

#if defined(BOARD_V8_LANDSCAPE)
  #define DEFAULT_DEVICE_PIN   "1991"
  #define SCREEN_WIDTH          240
  #define SCREEN_HEIGHT         240
  #define GRID_CENTER_X         120
  #define GRID_CENTER_Y         120
  #define BACKLIGHT_PIN         21
  #define SOFTAP_SSID           "DeskRadar-Setup-byGeGeLV"
  #define PORTAL_TITLE          "DeskRadar Setup by GeGeLv"
  #define FORCE_INSECURE_TLS    0

#elif defined(BOARD_MONKEY_PORTRAIT)
  #define DEFAULT_DEVICE_PIN   "1234"
  #define SCREEN_WIDTH          320
  #define SCREEN_HEIGHT         480
  #define GRID_CENTER_X          160
  #define GRID_CENTER_Y          240
  #define BACKLIGHT_PIN          27
  #define SOFTAP_SSID            "DeskRadar-Setup"
  #define PORTAL_TITLE           "DeskRadar Setup"
  #define FORCE_INSECURE_TLS     1

#else
  #error "Select a BOARD_* target in board_config.h"
#endif

#endif // BOARD_CONFIG_H
