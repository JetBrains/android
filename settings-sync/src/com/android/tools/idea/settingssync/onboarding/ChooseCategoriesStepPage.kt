/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.gct.login2.ui.onboarding.compose.HEADER_INLINE_ICON_KEY
import com.google.gct.login2.ui.onboarding.compose.InnerWizardContentPage
import com.google.gct.login2.ui.onboarding.compose.PageHeaderText
import com.google.gct.wizard.WizardPage
import com.google.gct.wizard.WizardPageControl
import com.google.gct.wizard.WizardState
import icons.StudioIllustrationsCompose
import org.jetbrains.jewel.ui.component.Text

internal class ChooseCategoriesStepPage : WizardPage() {
  override val description: String = "Backup & Sync step3: select what categories to sync."
  override val composableContent: @Composable WizardState.() -> Unit = {
    ChooseCategoriesComposableContent()
  }
  override val controlProvider: (WizardState) -> WizardPageControl = { state ->
    object : WizardPageControl() {
      override fun shouldShow(): Boolean {
        val configurationState = state.getOrCreateState { SyncConfigurationState() }

        with(configurationState) {
          return !state.canSkipFeatureConfiguration()
        }
      }
    }
  }
}

@Composable
internal fun SyncConfigurationPageTitle() =
  PageHeaderText(
    title =
      buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Configure ") }
        appendInlineContent(HEADER_INLINE_ICON_KEY)
        append(" Backup & Sync")
      },
    iconKey = StudioIllustrationsCompose.Common.GoogleLogo,
  )

@Composable
internal fun WizardState.ChooseCategoriesComposableContent() {
  InnerWizardContentPage(header = { SyncConfigurationPageTitle() }) {
    Column(Modifier.padding(vertical = 16.dp, horizontal = 32.dp)) {
      Text("You can configure what to sync to your Google account below.")

      Spacer(modifier = Modifier.height(8.dp))

      HierarchicalCheckboxes(
        getOrCreateState { SyncConfigurationState() }.syncCategoryStates,
        modifier = Modifier.padding(start = 16.dp),
      )
    }
  }
}
