#ifndef MINIMAL_SDL_H
#define MINIMAL_SDL_H

#include <stdint.h>

typedef enum {
    SDL_INIT_JOYSTICK = 0x00000200
} SDL_InitFlags;

typedef struct SDL_Joystick SDL_Joystick;

typedef enum {
    SDL_JOYSTICK_TYPE_UNKNOWN,
    SDL_JOYSTICK_TYPE_GAMECONTROLLER,
    SDL_JOYSTICK_TYPE_WHEEL,
    SDL_JOYSTICK_TYPE_ARCADE_STICK,
    SDL_JOYSTICK_TYPE_FLIGHT_STICK,
    SDL_JOYSTICK_TYPE_DANCE_PAD,
    SDL_JOYSTICK_TYPE_GUITAR,
    SDL_JOYSTICK_TYPE_DRUM_KIT,
    SDL_JOYSTICK_TYPE_ARCADE_PAD,
    SDL_JOYSTICK_TYPE_THROTTLE
} SDL_JoystickType;

typedef struct SDL_VirtualJoystickDesc {
    uint16_t version;
    uint16_t type;
    uint16_t naxes;
    uint16_t nbuttons;
    uint16_t nhats;
    uint16_t vendor_id;
    uint16_t product_id;
    uint16_t padding;
    uint32_t button_mask;
    uint32_t axis_mask;
    const char *name;
    void *userdata;
    void (*Update)(void *userdata);
    void (*SetPlayerIndex)(void *userdata, int player_index);
    int (*Rumble)(void *userdata, uint16_t low_frequency_rumble, uint16_t high_frequency_rumble);
    int (*RumbleTriggers)(void *userdata, uint16_t left_rumble, uint16_t right_rumble);
    int (*SetLED)(void *userdata, uint8_t red, uint8_t green, uint8_t blue);
    int (*SendEffect)(void *userdata, const void *data, int size);
} SDL_VirtualJoystickDesc;

#define SDL_VIRTUAL_JOYSTICK_DESC_VERSION 1

typedef struct SDL_version {
    uint8_t major;
    uint8_t minor;
    uint8_t patch;
} SDL_version;

#endif
