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
package com.android.tools.inspectors.common.api.actions

import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.idea.codenavigation.NavSource
import com.google.common.truth.Truth.assertThat
import com.intellij.pom.Navigatable
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

class NavigateToCodeActionTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  @Test
  fun update_noNavigatable() {
    val action = NavigateToCodeAction({ CodeLocation.stub() }, CodeNavigator(object : NavSource {
      override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
        return null
      }
    }, CodeNavigator.testExecutor))
    val event = TestActionEvent.createTestEvent()

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_notNavigatable() {
    val action = NavigateToCodeAction({ CodeLocation.stub() }, CodeNavigator(object : NavSource {
      override fun lookUp(location: CodeLocation, arch: String?): Navigatable {
        return object : Navigatable {
          override fun canNavigateToSource() = false
        }
      }
    }, CodeNavigator.testExecutor))
    val event = TestActionEvent.createTestEvent()

    action.update(event)

    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_navigatable() {
    val action = NavigateToCodeAction({ CodeLocation.stub() }, CodeNavigator(object : NavSource {
      override fun lookUp(location: CodeLocation, arch: String?): Navigatable {
        return object : Navigatable {
          override fun canNavigateToSource() = true
        }
      }
    }, CodeNavigator.testExecutor))
    val event = TestActionEvent.createTestEvent()

    action.update(event)

    assertThat(event.presentation.isEnabled).isTrue()
  }
}