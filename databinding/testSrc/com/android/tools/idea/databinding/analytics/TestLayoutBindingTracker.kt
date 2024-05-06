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
package com.android.tools.idea.databinding.analytics

import com.intellij.openapi.project.Project

/**
 * Overwrites methods in [LayoutBindingTracker] so it can be run in unit tests.
 *
 * Normally we would use dependency injection, but [LayoutBindingTracker] is a project service which
 * gets instantiated by IntelliJ.
 */
class TestLayoutBindingTracker constructor(project: Project) : LayoutBindingTracker(project) {
  override fun runInBackground(runnable: Runnable) {
    runnable.run()
  }
}
