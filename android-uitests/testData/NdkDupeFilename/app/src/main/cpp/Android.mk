LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE			:= hello-jni
LOCAL_SRC_FILES			:= hello-jni.c
LOCAL_STATIC_LIBRARIES	:= libapple libbanana

include $(BUILD_SHARED_LIBRARY)

LIBS_PATH := $(LOCAL_PATH)
include $(LIBS_PATH)/libapple/Android.mk
include $(LIBS_PATH)/libbanana/Android.mk
