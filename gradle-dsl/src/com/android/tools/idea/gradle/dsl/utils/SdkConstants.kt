/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("SdkConstants")

/**
 * This file replicates some constants from android.sdk.common: if anything is changed here then it should probably also be
 * changed in that module.
 */
package com.android.tools.idea.gradle.dsl.utils

const val FN_BUILD_GRADLE = "build.gradle"
const val FN_BUILD_GRADLE_KTS = "build.gradle.kts"
const val FN_GRADLE_PROPERTIES = "gradle.properties"
const val FN_SETTINGS_GRADLE = "settings.gradle"
const val FN_SETTINGS_GRADLE_KTS = "settings.gradle.kts"
const val FN_DECLARATIVE_BUILD_GRADLE = "build.gradle.toml"
const val FN_DECLARATIVE_SETTINGS_GRADLE = "settings.gradle.toml"

const val EXT_VERSIONS_TOML = "versions.toml"
const val EXT_DECLARATIVE_TOML = "gradle.toml"

const val GRADLE_PATH_SEPARATOR = ":"