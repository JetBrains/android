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
package com.android.tools.idea.imports

/**
 * Base URL to Google Maven Class Index.
 *
 * The full URL is [BASE_URL]/[RELATIVE_PATH].
 */
internal const val BASE_URL = "https://dl.google.com/android/studio/gmaven/index/release"

/**
 * Base name of Google Maven Class Index file.
 *
 * The full file name is [NAME]-v[VERSION].json.gz.
 */
internal const val NAME = "classes"

/**
 * Version of Google Maven Class Index file.
 */
internal const val VERSION = 0.1

/**
 * Relative URL to Google Maven Class Index.
 *
 * The full URL is [BASE_URL]/[RELATIVE_PATH].
 */
internal const val RELATIVE_PATH = "v$VERSION/$NAME-v$VERSION.json.gz"

/**
 * File name of the offline version of Google Maven Class Index.
 *
 * The full path is //tools/adt/idea/android/resources/gmavenIndex/[OFFLINE_NAME].json.
 */
internal const val OFFLINE_NAME = "classes-offline"