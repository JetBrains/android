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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.Promo
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import icons.StudioIcons
import javax.swing.Icon
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

class StudioLabsSettingsConfigurable :
  BoundSearchableConfigurable(
    displayName = "Studio Labs",
    helpTopic = "Sign up for new features",
    _id = "studio.settings.StudioLabsConfigurable",
  ),
  Promo,
  Disposable {
  @VisibleForTesting val panelList = getStudioLabsFeaturePanelList()

  override fun createPanel(): DialogPanel {
    log(PageInteraction.OPENED)
    return panel { row { cell(StudioComposePanel { StudioLabsPanel() }) } }
  }

  override fun getHelpTopic(): String? {
    return AndroidWebHelpProvider.HELP_PREFIX + "studio/preview/gemini/labs"
  }

  @Composable
  fun StudioLabsPanel() {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.background(JewelTheme.globalColors.panelBackground)) {
      Text("Opt in to Studio Labs to get early access to experimental features.")
      Column(modifier = Modifier.verticalScroll(scrollState)) {
        Spacer(modifier = Modifier.size(12.dp))
        panelList.chunked(2).forEach { item ->
          Row(modifier = Modifier.padding(bottom = 8.dp)) {
            item.forEach {
              it.PanelContent()
              Spacer(modifier = Modifier.size(8.dp))
            }
          }
        }
      }
    }
  }

  override fun getPromoIcon(): Icon {
    return StudioIcons.Shell.Menu.STUDIO_LABS
  }

  override fun isModified(): Boolean {
    return panelList.any { it.isModified() }
  }

  override fun apply() { // Handles both apply and Ok button clicks
    log(PageInteraction.APPLY_BUTTON_CLICKED)
    panelList.forEach { it.apply() }
  }

  override fun reset() {
    panelList.forEach { it.reset() }
  }

  override fun dispose() {}

  companion object {
    fun isThereAnyFeatureInLabs(): Boolean {
      return getStudioLabsFeaturePanelList().isNotEmpty()
    }

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
        listOf<StudioLabsFeaturePanelUi>(
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
            imageSourceDefault = "images/studio_labs/generate-compose-preview.png",
            imageSourceDark = "images/studio_labs/generate-compose-preview_dark.png",
            imageDescription = "Generate Compose Preview menu",
          ),
          StudioLabsFeaturePanelUi(
            flag = StudioFlags.STUDIOBOT_ATTACHMENTS,
            heading = "Attach Images",
            description =
              """
                Allows attaching images to the Gemini queries.
              """
                .trimIndent(),
            imageSourceDefault = "images/studio_labs/attach-image.png",
            imageSourceDark = "images/studio_labs/attach-image_dark.png",
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
            imageSourceDefault = "images/studio_labs/at-file.png",
            imageSourceDark = "images/studio_labs/at-file_dark.png",
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
            imageSourceDefault = "images/studio_labs/suggested-fix.png",
            imageSourceDark = "images/studio_labs/suggested-fix_dark.png",
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
              imageSourceDefault = "images/studio_labs/prompt-library-settings.png",
              imageSourceDark = "images/studio_labs/prompt-library-settings_dark.png",
              imageDescription = "Prompt Library settings",
            )
          )
      }

      return labsFeatures
    }
  }
}
