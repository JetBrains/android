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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.idea.codenavigation.FakeNavSource
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView
import javax.swing.JComponent
import javax.swing.JPanel

class StubUiComponentsProvider : UiComponentsProvider {
  override val codeNavigator = CodeNavigator(FakeNavSource(), CodeNavigator.testExecutor)

  override fun createStackTraceView(model: StackTraceModel): StackTraceView {
    return StackTraceGroupStub().createStackView(model)
  }
}

class StackTraceGroupStub : StackTraceGroup {
  override fun createStackView(model: StackTraceModel): StackTraceView {
    return StackTraceViewStub(model)
  }
}

class StackTraceViewStub(private val model: StackTraceModel) : StackTraceView {
  private val component = JPanel()

  override fun getModel(): StackTraceModel {
    return model
  }

  override fun getComponent(): JComponent {
    return component
  }
}
