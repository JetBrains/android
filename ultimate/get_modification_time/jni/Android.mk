LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := get_modification_time
LOCAL_SRC_FILES := get_modification_time.c

include $(BUILD_EXECUTABLE)
