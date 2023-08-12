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
package com.android.tools.idea.res

import java.nio.file.Path
import java.util.concurrent.Executor

/**
 * Caching parameters used by [ResourceFolderRepository]
 *
 * @param cacheFile The location of the cache file.
 * @param cacheIsInvalidated The cache is invalidated and should not be used for loading.
 * @param codeVersion The version of the Android plugin, used to make sure that the cache file is updated
 *     when the code changes. This version is an additional safety measure on top of
 *     [ResourceFolderRepository.CACHE_FILE_FORMAT_VERSION].
 * @param cacheCreationExecutor The executor used for creating a cache file, or null if the cache file
 *     should not be created if it doesn't exist or is out of date.
 */
class ResourceFolderRepositoryCachingData(val cacheFile: Path,
                                          private val cacheIsInvalidated: Boolean,
                                          val codeVersion: String,
                                          val cacheCreationExecutor: Executor? = null)
