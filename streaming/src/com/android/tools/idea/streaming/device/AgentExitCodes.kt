/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.streaming.device

// Predefined agent's exit codes. Other exit codes are possible.
internal const val AGENT_GENERIC_FAILURE = 1
internal const val AGENT_INVALID_COMMAND_LINE = 2
internal const val AGENT_SOCKET_CONNECTIVITY_ERROR = 10
internal const val AGENT_SOCKET_IO_ERROR = 11
internal const val AGENT_INVALID_CONTROL_MESSAGE = 12
internal const val AGENT_NULL_POINTER = 20
internal const val AGENT_CLASS_NOT_FOUND = 21
internal const val AGENT_METHOD_NOT_FOUND = 22
internal const val AGENT_CONSTRUCTOR_NOT_FOUND = 23
internal const val AGENT_FIELD_NOT_FOUND = 24
internal const val AGENT_JAVA_EXCEPTION = 25
internal const val AGENT_VIDEO_ENCODER_NOT_FOUND = 30
internal const val AGENT_VIDEO_ENCODER_INITIALIZATION_ERROR = 31
internal const val AGENT_VIDEO_ENCODER_CONFIGURATION_ERROR = 32
internal const val AGENT_WEAK_VIDEO_ENCODER = 33
internal const val AGENT_REPEATED_VIDEO_ENCODER_ERRORS = 34
internal const val AGENT_VIDEO_ENCODER_START_ERROR = 35
internal const val AGENT_VIRTUAL_DISPLAY_CREATION_ERROR = 50
internal const val AGENT_INPUT_SURFACE_CREATION_ERROR = 51
internal const val AGENT_SERVICE_NOT_FOUND = 52
internal const val AGENT_KEY_CHARACTER_MAP_ERROR = 53
internal const val AGENT_XR_DEVICE_IS_NOT_CONFIGURED_FOR_MIRRORING = 54
internal const val AGENT_SIGABORT = 134
internal const val AGENT_SIGKILL = 137
internal const val AGENT_SIGSEGV = 139