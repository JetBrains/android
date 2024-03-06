/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.preview.animation.AnimationPreviewState
import com.android.tools.idea.preview.animation.Card
import com.android.tools.idea.preview.animation.TimelinePanel
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JLabel
import org.junit.Assert.assertTrue

val NoopComposeAnimationTracker =
  ComposeAnimationTracker(AnimationToolingUsageTracker.getInstance(null))

object TestUtils {

  fun testPreviewState() =
    object : AnimationPreviewState {
      override val currentTime: Int
        get() = 0
    }

  fun createPlaybackPlaceHolder() =
    JLabel("Playback placeholder").apply { background = JBColor.blue }

  fun createTimelinePlaceHolder() =
    JLabel("Timeline placeholder").apply { background = JBColor.pink }

  fun createComposeAnimation(
    label: String? = null,
    type: ComposeAnimationType = ComposeAnimationType.ANIMATED_VALUE,
  ) =
    object : ComposeAnimation {
      override val animationObject = Any()
      override val type = type
      override val label = label
      override val states = setOf(Any())
    }

  fun assertBigger(minimumSize: Dimension, actualSize: Dimension) =
    assertTrue(minimumSize.width <= actualSize.width && minimumSize.height <= actualSize.height)

  fun findAllCards(parent: Component): List<Card> =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is Card }
      .collect(Collectors.toList())
      .map { it as Card }

  fun Component.findToolbar(place: String): ActionToolbarImpl {
    return TreeWalker(this)
      .descendantStream()
      .filter { it is ActionToolbarImpl }
      .collect(Collectors.toList())
      .map { it as ActionToolbarImpl }
      .first { it.place == place }
  }

  fun findTimeline(parent: Component): TimelinePanel =
    TreeWalker(parent)
      .descendantStream()
      .filter { it is TimelinePanel }
      .collect(Collectors.toList())
      .map { it as TimelinePanel }
      .first()

  fun Card.findLabel(): JLabel =
    TreeWalker(this.component)
      .descendantStream()
      .filter { it is JLabel }
      .collect(Collectors.toList())
      .map { it as JLabel }
      .first()

  fun AnimationCard.findExpandButton(): Component {
    return (this.component.components[0] as Container).components[0]
  }

  fun Component.findComboBox(): ComboBoxAction.ComboBoxButton =
    TreeWalker(this)
      .descendantStream()
      .filter { it is ComboBoxAction.ComboBoxButton }
      .collect(Collectors.toList())
      .map { it as ComboBoxAction.ComboBoxButton }
      .first()
}
