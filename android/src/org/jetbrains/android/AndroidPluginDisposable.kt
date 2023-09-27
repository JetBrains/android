/*
 * Copyright (C) 2022 The Android Open Source Project
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
package org.jetbrains.android

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.application

/**
 * If you are writing Android plugin code and are in a situation where you want to
 * use the `Application` or `Project` as a parent disposable, then use this class instead.
 *
 * Background: the [IntelliJ SDK Docs](https://plugins.jetbrains.com/docs/intellij/disposers.html)
 * say to never use `Application` nor `Project` as parent disposables, because this can prevent
 * plugin unloading. Technically, we do not care about Android plugin unloading in Android Studio.
 * However, JetBrains cares about plugin unloading in their fork of the Android plugin in IntelliJ.
 */
@Service
class AndroidPluginDisposable private constructor() : Disposable {

  companion object {
    @JvmStatic
    fun getApplicationInstance(): AndroidPluginDisposable {
      return application.getService(AndroidPluginDisposable::class.java)
    }

    @JvmStatic
    fun getProjectInstance(project: Project): AndroidPluginDisposable {
      return project.getService(AndroidPluginDisposable::class.java)
    }
  }

  override fun dispose() {}
}
