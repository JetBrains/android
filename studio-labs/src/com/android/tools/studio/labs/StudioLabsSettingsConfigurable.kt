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
package com.android.tools.studio.labs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.STUDIO_LABS_EVENT
import com.google.wireless.android.sdk.stats.StudioLabsEvent
import com.google.wireless.android.sdk.stats.StudioLabsEvent.PageInteraction
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.Promo
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.NlsContexts
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

class StudioLabsSettingsConfigurable :
  SearchableConfigurable, Promo, Configurable.NoScroll, Configurable.NoMargin {
  @VisibleForTesting val panelList = getStudioLabsFeaturePanelList()

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = "Studio Labs"

  override fun getId(): @NonNls String = "studio.settings.StudioLabsConfigurable"

  override fun getHelpTopic(): String =
    AndroidWebHelpProvider.HELP_PREFIX + "studio/preview/gemini/labs"

  override fun createComponent(): JComponent {
    log(PageInteraction.OPENED)
    return StudioComposePanel { StudioLabsPanel() }
  }

  @OptIn(ExperimentalJewelApi::class)
  @Composable
  private fun StudioLabsPanel() {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(JewelTheme.globalColors.panelBackground)
          // Matches the borders applied in ConfigurableCardPanel.createConfigurableComponent
          .padding(start = 16.dp, top = 5.dp, end = 16.dp, bottom = 10.dp)
    ) {
      Text("Opt in to Studio Labs to get early access to experimental features.")

      Spacer(modifier = Modifier.height(12.dp))

      val state = rememberLazyGridState()
      VerticallyScrollableContainer(state, Modifier.fillMaxWidth()) {
        LazyVerticalGrid(
          GridCells.Adaptive(300.dp),
          Modifier.fillMaxWidth(),
          state,
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(panelList.size) { index -> panelList[index].PanelContent() }
        }
      }
    }
  }

  override fun getPromoIcon(): Icon = StudioIcons.Shell.Menu.STUDIO_LABS

  override fun isModified(): Boolean = panelList.any { it.isModified() }

  override fun apply() { // Handles both apply and Ok button clicks
    log(PageInteraction.APPLY_BUTTON_CLICKED)
    panelList.forEach { it.apply() }
  }

  override fun reset() {
    panelList.forEach { it.reset() }
  }

  companion object {
    fun isThereAnyFeatureInLabs(): Boolean = getStudioLabsFeaturePanelList().isNotEmpty()

    private fun log(pageInteraction: PageInteraction) {
      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setKind(STUDIO_LABS_EVENT)
          .setStudioLabsEvent(StudioLabsEvent.newBuilder().setPageInteraction(pageInteraction))
      )
    }

    /**
     * Returns feature panels to display on Studio Labs page. If
     * [StudioFlags.STUDIO_LABS_SETTINGS_FAKE_FEATURE_ENABLED] is set to true, returns a fake
     * feature panel list for internal testing.
     */
    private fun getStudioLabsFeaturePanelList(): List<StudioLabsFeaturePanelUi> {
      val labsFeatures =
        listOf(
          // Add a pane for every feature that should be in labs.
          // e.g.,
          //    StudioLabsFeaturePanelUi(
          //      flag = StudioFlags.STUDIOBOT_PROMPT_LIBRARY_ENABLED,
          //      heading = "Prompt Library",
          //      description =
          //      "Allows to store frequently used prompts for quick access." +
          //      " Optionally share prompts with other people working on a same project.",
          //      imageSourceDefault = "images/studio_labs/prompt-library-settings.png",
          //      imageSourceDark = "images/studio_labs/prompt-library-settings_dark.png",
          //      imageDescription = "Prompt Library settings",
          //    )
          StudioLabsFeaturePanelUi(
            flag = StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW,
            heading = "Generate Compose Preview",
            description =
              """
                Allows the generation of new Compose Previews for existing Composables.
              """
                .trimIndent(),
            imageKey = StudioLabsIcons.Features.GenerateComposePreview,
            imageDescription = "Generate Compose Preview menu",
          ),
          StudioLabsFeaturePanelUi(
            flag = StudioFlags.COMPOSE_PREVIEW_TRANSFORM_UI_WITH_AI,
            heading = "Transform UI with AI",
            description =
              """
              Allows the transformation of existing UI within the Compose Preview environment using Gemini.
            """
                .trimIndent(),
            imageKey = StudioLabsIcons.Features.TransformComposePreview,
            imageDescription = "Transform UI with Gemini action",
          ),
          StudioLabsFeaturePanelUi(
            flag = StudioFlags.STUDIOBOT_ATTACHMENTS,
            heading = "Attach Images",
            description =
              """
                Allows attaching images to the Gemini queries.
              """
                .trimIndent(),
            imageKey = StudioLabsIcons.Features.AttachImage,
            imageDescription = "Image attaching menu",
          ),
          StudioLabsFeaturePanelUi(
            flag = StudioFlags.STUDIOBOT_CONTEXT_ATTACHMENT_ENABLED,
            heading = "Context Management",
            description =
              """
                Allows attaching files from your project to Gemini queries, and storing them in a context drawer.
              """
                .trimIndent(),
            imageKey = StudioLabsIcons.Features.AtFile,
            imageDescription = "@file attaching menu",
          ),
          /*
          Disabled pending move to agent
          StudioLabsFeaturePanelUi(
            flag = StudioFlags.SUGGEST_A_FIX,
            heading = "Generate suggested fix in AQI",
            description =
              """
                Enables AQI to generate suggested fixes based on Gemini insight.
              """
                .trimIndent(),
            imageKey = StudioLabsIcons.Features.SuggestedFix,
            imageDescription = "Suggested Fix in AQI",
          ),
          */
        )

      if (StudioFlags.STUDIO_LABS_SETTINGS_FAKE_FEATURE_ENABLED.get()) {
        // Add a fake feature so that QA can test out Studio Labs in scenarios
        // where there are no real features available under Labs.
        return labsFeatures +
          listOf(
            FakeStudioLabsFeaturePanelUi(
              flag = StudioFlags.STUDIOBOT_PROMPT_LIBRARY_ENABLED,
              heading = "(Test only) Prompt Library",
              description =
                "Allows to store frequently used prompts for quick access." +
                  " Optionally share prompts with other people working on a same project.",
              imageKey = StudioLabsIcons.Features.PromptLibrarySettings,
              imageDescription = "Prompt Library settings",
            )
          )
      }

      return labsFeatures
    }
  }
}
