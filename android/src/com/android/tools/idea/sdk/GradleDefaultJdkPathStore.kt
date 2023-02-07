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
package com.android.tools.idea.sdk

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service

private const val GRADLE_JDK_PATH_KEY = "gradle.jdk.path"

/**
 * Simple persistence based on [PropertiesComponent] for the default JDK used to run Gradle daemon.
 */
@Service(Service.Level.APP)
object GradleDefaultJdkPathStore {

  var jdkPath: String?
    get() = PropertiesComponent.getInstance().getValue(GRADLE_JDK_PATH_KEY)
    set(path) {
      PropertiesComponent.getInstance().setValue(GRADLE_JDK_PATH_KEY, path)
    }
}
