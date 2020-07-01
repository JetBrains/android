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
package com.android.tools.idea.adb

import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.util.function.Supplier

/**
 * Supplier of ADB executable path for a given [Project] instance.
 *
 * Usage:
 *  `val adbFile: File? = AdbFileProvider.fromProject(project)?.adbFile`
 */
data class AdbFileProvider(private val supplier: Supplier<File?>) {
  companion object {
    private val KEY: Key<AdbFileProvider> = Key.create(::KEY.qualifiedName)

    @JvmStatic fun fromProject(project: Project): AdbFileProvider? = project.getUserData(KEY)
  }

  val adbFile: File?
    get() = supplier.get()

  fun storeInProject(project: Project) {
    project.putUserData(KEY, this)
  }
}
