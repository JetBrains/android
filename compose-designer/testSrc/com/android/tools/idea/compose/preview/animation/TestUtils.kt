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
import com.android.tools.idea.preview.animation.Card
import com.android.tools.idea.preview.animation.TimelinePanel
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import java.awt.Component
import java.awt.Dimension
import java.util.stream.Collectors
import javax.swing.JLabel
import org.junit.Assert.assertTrue

val NoopComposeAnimationTracker =
  ComposeAnimationTracker(AnimationToolingUsageTracker.getInstance(null))

object TestUtils {

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

  fun Component.findComboBox(): ComboBoxAction.ComboBoxButton =
    TreeWalker(this)
      .descendantStream()
      .filter { it is ComboBoxAction.ComboBoxButton }
      .collect(Collectors.toList())
      .map { it as ComboBoxAction.ComboBoxButton }
      .first()
}
