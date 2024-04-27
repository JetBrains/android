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
package com.android.tools.idea.startup

import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import org.junit.ClassRule
import org.junit.Test

class SuppressTipOfTheDayTest {
  companion object {
    @JvmStatic
    @get:ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  @Suppress("UnstableApiUsage")
  fun popupDisabled() {
    val tipAndTrickManager = TipAndTrickManager.getInstance()
    assertThat(tipAndTrickManager).isInstanceOf(SuppressTipOfTheDay.OverridingTipAndTrickManager::class.java)
    assertThat(tipAndTrickManager.canShowDialogAutomaticallyNow(mock<Project>())).isFalse()
  }

  @Test
  fun actionDisabled() {
    val showTipsAction = ActionManager.getInstance().getAction(SuppressTipOfTheDay.SHOW_TIPS_ACTION_ID)
    assertThat(showTipsAction).isInstanceOf(EmptyAction::class.java)
  }
}
