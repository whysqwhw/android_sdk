LOCAL_PATH:= $(call my-dir)

$(call emugl-begin-host-executable,triangleCM)
$(call emugl-import,libEGL_translator libGLES_CM_translator)

PREBUILT := $(HOST_PREBUILT_TAG)
LOCAL_SDL_CONFIG ?= prebuilts/tools/$(PREBUILT)/sdl/bin/sdl-config
LOCAL_SDL_CFLAGS := $(shell $(LOCAL_SDL_CONFIG) --cflags)
LOCAL_SDL_LDLIBS := $(filter-out %.a %.lib,$(shell $(LOCAL_SDL_CONFIG) --static-libs))

ifeq ($(HOST_OS),darwin)
  # OS X 10.6+ needs to be forced to link dylib to avoid problems
  # with the dynamic function lookups in SDL 1.2
  LOCAL_SDL_LDLIBS += /usr/lib/dylib1.o
endif

LOCAL_SRC_FILES:= \
        triangleCM.cpp

LOCAL_CFLAGS += $(LOCAL_SDL_CFLAGS) -g -O0
LOCAL_LDLIBS += $(LOCAL_SDL_LDLIBS)

LOCAL_STATIC_LIBRARIES += libSDL libSDLmain

ifeq ($(HOST_OS),darwin)
$(call emugl-import,libMac_view)
endif

$(call emugl-end-module)
