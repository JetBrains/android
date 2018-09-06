/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.ide.common.util.PathString
import com.intellij.openapi.module.Module
import java.io.IOException

/**
 * A [SampleDataDirectoryProvider] determines the proper location to store a [Module]'s sample data.
 */
interface SampleDataDirectoryProvider {
  /**
   * Returns the path where the [Module]'s sample data directory will be if it exists,
   * or null if the path can't be determined.
   */
  fun getSampleDataDirectory(): PathString?

  /**
   * Attempts to create the [Module]'s sample data directory if it does not exist.
   * This method returns the path where the [Module]'s sample data directory will be if
   * it exists, or null if the path can't be determined.
   *
   * This function must be called in a write action.
   */
  @Throws(IOException::class)
  fun getOrCreateSampleDataDirectory(): PathString?
}