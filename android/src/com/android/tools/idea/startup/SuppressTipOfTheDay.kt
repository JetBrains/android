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

import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/** Suppresses tip-of-the-day, which does not work correctly in Android Studio (b/302571384). */
class SuppressTipOfTheDay {

  /** Suppresses the "Tip of the Day" action / menu item. */
  class ActionSuppressor : ActionConfigurationCustomizer {
    override fun customize(actionManager: ActionManager) {
      checkNotNull(actionManager.getActionOrStub(SHOW_TIPS_ACTION_ID))
      actionManager.replaceAction(SHOW_TIPS_ACTION_ID, EmptyAction())
    }
  }

  /** Suppresses the automatic tip-of-the-day popup. */
  @Suppress("UnstableApiUsage")
  class OverridingTipAndTrickManager : TipAndTrickManager {
    override fun canShowDialogAutomaticallyNow(project: Project): Boolean = false
    override suspend fun showTipDialog(project: Project?) { warnSuppressed() }
    override suspend fun showTipDialog(project: Project, tip: TipAndTrickBean) { warnSuppressed() }
    override fun closeTipDialog() { warnSuppressed() }
    private fun warnSuppressed() { thisLogger().warn("TipAndTrickManager is suppressed in Android Studio") }
  }

  companion object {
    internal const val SHOW_TIPS_ACTION_ID = "ShowTips"
  }
}
