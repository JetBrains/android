/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.model

import com.android.tools.idea.assistant.DefaultTutorialBundle
import com.android.tools.idea.whatsnew.assistant.WhatsNewBundle

/**
 * What's New V2 is rendered in Compose instead of the Assistant tool window framework, so this converts the Assistant tutorial bundle to a
 * more Compose-friendly model structure.
 */
fun WhatsNewBundle.toWhatsNewData(): WhatsNewData {
  val dataBuilder = WhatsNewData.Builder()

  // Start at the bundle. We should only have 1 feature, and its values don't matter for the UI.
  this.features.firstOrNull()?.let { feature ->
    feature.tutorials.firstOrNull()?.let { tutorial ->
      // The tutorial is essentially the whole panel
      dataBuilder.label(tutorial.label.trimIndent().trim())
      tutorial.description?.let { description -> dataBuilder.description(description.trimIndent().trim()) }
      dataBuilder.remoteLink(tutorial.remoteLink)
      dataBuilder.remoteLinkLabel(tutorial.remoteLinkLabel)

      // Each bundle step is a card in the UI
      tutorial.steps.forEach { step ->
        if (step is DefaultTutorialBundle.FooterStep) {
          step.stepElements.firstOrNull()?.section?.let { section -> dataBuilder.footerText(section.trimIndent().trim()) }
        } else {
          dataBuilder.card {
            title(step.label.trimIndent().trim())

            // In the bundle, each step element can have multiple elements inside, which might not be totally correct, but it should be ok
            // to support it. For elements that we should only have one of, just use the final one found.
            step.stepElements.forEach { stepElement ->
              // Should only have 1 description
              stepElement.section?.let { section -> description(section.trimIndent().trim()) }

              // Should only have 1 image
              stepElement.image?.let { img ->
                image {
                  img.source?.let { source(it) }
                  img.description?.let { description(it) }
                }
              }

              // Can have multiple actions
              stepElement.action?.let { action ->
                action {
                  label(action.label)
                  key(action.key)
                  action.actionArgument?.let { actionArgument(it) }
                  action.successMessage?.let { successMessage(it) }
                  highlighted(action.isHighlighted)
                }
              }
            }
          }
        }
      }
    }
  }
  return dataBuilder.build()
}
