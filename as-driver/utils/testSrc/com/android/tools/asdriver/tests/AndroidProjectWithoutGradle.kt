/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.asdriver.tests

import java.io.IOException

/**
 * Project wrapper similar to [AndroidProject], except that it does not try to modify the
 * target project to inject Gradle files and properties. Suitable for use with existing
 * APK projects that don't use Gradle.
 */
class AndroidProjectWithoutGradle(path: String) : AndroidProject(path) {
  @Throws(IOException::class)
  override fun injectGradle() {
    // Do nothing. APK projects do not use Gradle.
  }
}