/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.adb.AdbFileProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import java.io.File
import org.junit.rules.ExternalResource
import org.mockito.kotlin.mock

/** Rule required for making AdbUtils.getAdbFuture(Project). */
class AdbFileProviderRule(private val projectSupplier: () -> Project) : ExternalResource() {
  private var serviceDisposable: Disposable? = null

  override fun before() {
    val adbFile: File = mock()
    val disposable = Disposer.newDisposable().also { serviceDisposable = it }
    val adbFileProvider = AdbFileProvider { adbFile }
    projectSupplier().replaceService(AdbFileProvider::class.java, adbFileProvider, disposable)
  }

  override fun after() {
    serviceDisposable?.let { Disposer.dispose(it) }
  }
}
