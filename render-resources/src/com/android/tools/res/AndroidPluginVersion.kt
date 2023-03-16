/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("AndroidPluginVersion")
package com.android.tools.res

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

private const val ANDROID_PLUGIN_ID = "org.jetbrains.android"

/**
 * Returns the version of the Android IDE plugin, or null if not running inside an IDE.
 */
fun getAndroidPluginVersion() =
    PluginManagerCore.getPlugin(PluginId.getId(ANDROID_PLUGIN_ID))?.version
