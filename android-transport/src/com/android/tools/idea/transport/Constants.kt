/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("Constants")

package com.android.tools.idea.transport

@JvmField
val TRANSPORT_DEV_DIR = "bazel-bin/tools/base/transport/android"
@JvmField
val TRANSPORT_RELEASE_DIR = "plugins/android/resources/transport"

@JvmField
val PERFA_DEV_DIR = "bazel-bin/tools/base/profiler/app"
@JvmField
val PERFA_RELEASE_DIR = "plugins/android/resources"

@JvmField
val JVMTI_AGENT_DEV_DIR = "bazel-bin/tools/base/transport/native/agent/android"
@JvmField
val JVMTI_AGENT_RELEASE_DIR = "plugins/android/resources/transport/native/agent"

@JvmField
val SIMPLEPERF_DEV_DIR = "prebuilts/tools/common/simpleperf"
@JvmField
val SIMPLEPERF_RELEASE_DIR = "plugins/android/resources/simpleperf"

@JvmField
val PERFETTO_DEV_DIR = "prebuilts/tools/common/perfetto"
@JvmField
val PERFETTO_RELEASE_DIR = "plugins/android/resources/perfetto"
