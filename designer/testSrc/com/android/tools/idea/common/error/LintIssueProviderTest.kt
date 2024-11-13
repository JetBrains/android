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
package com.android.tools.idea.common.error

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.lint.common.ModCommandLintQuickFix
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test

class LintIssueProviderTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testQuickFixCreation() {
    val action =
      object : ModCommandAction {
        override fun getPresentation(context: ActionContext): Presentation {
          return Presentation.of("presentation")
        }

        override fun perform(context: ActionContext): ModCommand {
          return ModCommand.psiUpdate(context) {}
        }

        override fun getFamilyName(): @IntentionFamilyName String {
          return "familyName"
        }
      }
    val modQuickFix = ModCommandLintQuickFix(action)
    val lintAnnotationsModel = LintAnnotationsModel()
    val nlModel =
      NlModelBuilderUtil.model(
          projectRule,
          SdkConstants.FD_RES_LAYOUT,
          "model.xml",
          ComponentDescriptor("LinearLayout"),
        )
        .build()
    MockIssueFactory.addLintIssue(
      lintAnnotationsModel,
      HighlightDisplayLevel.ERROR,
      NlComponent(nlModel, 0),
    )
    val wrapper = LintIssueProvider.LintIssueWrapper(lintAnnotationsModel.issues[0])
    val fix = wrapper.createQuickFixPair(modQuickFix)
    assertNotNull(fix)
    assertEquals("presentation", fix.description)
  }
}
