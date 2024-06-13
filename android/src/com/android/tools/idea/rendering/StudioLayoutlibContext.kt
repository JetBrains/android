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
package com.android.tools.idea.rendering

import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.layoutlib.LayoutlibContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

/** Studio-specific implementation of [LayoutlibContext]. */
class StudioLayoutlibContext(private val project: Project) : LayoutlibContext {
  private val hasRegistered = AtomicBoolean(false)

  override fun hasLayoutlibCrash(): Boolean = hasStudioLayoutlibCrash()
  override fun register(layoutlib: LayoutLibrary) {
    if (!hasRegistered.getAndSet(true)) {
      if (!project.isDisposed)
        Disposer.register((project as ProjectEx).earlyDisposable) { layoutlib.dispose() }
      else {
        Logger.getInstance(StudioLayoutlibContext::class.java).error("$project had already been disposed")
        layoutlib.dispose()
      }
    } else {
      Logger.getInstance(StudioLayoutlibContext::class.java).error("A duplicate Layoutlib is created for project $project")
    }
  }
}