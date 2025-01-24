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
package com.android.screenshottest

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.util.MultiParentDisposable
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

@OptIn(ExperimentalJewelApi::class)
class ScreenshotTestDetailsView(project: Project, parentDisposable: MultiParentDisposable) {

  val rawTestLogConsoleView: ConsoleViewImpl = ConsoleViewImpl(project, /*viewer=*/true).apply {
    Disposer.register(parentDisposable, this)
  }
}

