/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("Annotations")
package com.android.tools.sdk

import com.android.sdklib.IAndroidTarget

/**
 * Indicates whether annotations.jar needs to be added to the classpath of an Android SDK. annotations.jar is not needed for API 16
 * or newer. The annotations are already included in android.jar.
 */
fun IAndroidTarget.needsAnnotationsJarInClasspath(): Boolean = this.version.apiLevel <= 15