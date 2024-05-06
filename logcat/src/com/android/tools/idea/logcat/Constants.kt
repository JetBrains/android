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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogcatHeader
import com.intellij.openapi.util.Key
import java.time.Instant

/** Bucket for various global constants */
internal val TAGS_PROVIDER_KEY = Key<TagsProvider>("TagProvider")

internal val PACKAGE_NAMES_PROVIDER_KEY = Key<PackageNamesProvider>("PackageNamesProvider")

internal val PROCESS_NAMES_PROVIDER_KEY = Key<ProcessNamesProvider>("ProcessNamesProvider")

@JvmField internal val SYSTEM_HEADER = LogcatHeader(LogLevel.INFO, 0, 0, "", "", "", Instant.EPOCH)
