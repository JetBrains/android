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
package com.android.tools.adtui.swing.popup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import org.junit.rules.ExternalResource

/**
 * A rule that sets up using [FakeJBPopupFactory] in a test.
 */
class JBPopupRule : ExternalResource() {

  val disposable = Disposer.newDisposable()
  val fakePopupFactory = FakeJBPopupFactory(disposable)

  override fun before() {
    ApplicationManager.getApplication().replaceService(JBPopupFactory::class.java, fakePopupFactory, disposable)
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}
