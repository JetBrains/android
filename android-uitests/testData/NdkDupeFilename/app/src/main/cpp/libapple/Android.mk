LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE			:= libapple
LOCAL_SRC_FILES			:= src/pie.c
LOCAL_C_INCLUDES		:= include
LOCAL_EXPORT_C_INCLUDES := include

include $(BUILD_STATIC_LIBRARY)
