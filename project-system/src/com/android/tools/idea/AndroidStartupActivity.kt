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
package com.android.tools.idea

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * A startup activity which should run when an Android project is opened or an already existing project becomes an Android project.
 */
interface AndroidStartupActivity {
  companion object {
    @JvmField
    val STARTUP_ACTIVITY: ExtensionPointName<AndroidStartupActivity> = ExtensionPointName.create("com.android.androidStartupActivity")
  }

  /**
   * Runs when [project] being an Android project is opened or [project] becomes and Android project.
   *
   * [disposable] should be used to register any disposables which should be disposed when [project] loses its last Android module (if the
   * project system supports it) or when the project is disposed.
   */
  // TODO(b/150626707): Update the JavaDoc when disposing works as intended.
  @UiThread
  fun runActivity(project: Project, disposable: Disposable)
}