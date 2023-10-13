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
package com.android.tools.idea.preview.modes

import com.android.tools.idea.compose.preview.LayoutMode
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.Colors
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.actionSystem.DataKey
import java.awt.Color

/**
 * Interface used for Preview Representations that support [PreviewMode]s. Classes implementing this
 * interface can only be in a single [PreviewMode] at a time, such as [PreviewMode.Default] or
 * [PreviewMode.Interactive].
 */
interface PreviewModeManager {
  /**
   * The current [PreviewMode]. This can be a [PreviewMode.Transitive] mode if the manager is
   * transitioning from one [PreviewMode.Settable] to another. It can also be a
   * [PreviewMode.Settable] mode.
   */
  val mode: PreviewMode

  /**
   * The current [PreviewMode.Settable] mode if the manager is not transitioning from one mode to
   * another or the next [PreviewMode.Settable] mode that will be set after a transition if the
   * manager is currently transitioning from one mode to another. This is to help determine certain
   * values that are defined in the "next" mode. For example, when transitioning to the
   * [PreviewMode.UiCheck] mode, we need to check the value of
   * [PreviewMode.UiCheck.atfChecksEnabled] before the transition is finished.
   */
  val currentOrNextMode: PreviewMode.Settable
    get() =
      when (val currentMode = mode) {
        is PreviewMode.Switching -> currentMode.newMode
        is PreviewMode.Settable -> currentMode
      }

  /**
   * Indicates whether the preview is in its default mode by opposition to one of the special modes
   * (interactive, animation, UI check). Both [PreviewMode.Default] and [PreviewMode.Gallery] are
   * normal modes.
   */
  val isInNormalMode: Boolean
    get() = mode is PreviewMode.Default || mode is PreviewMode.Gallery

  /**
   * Changes the current mode to [newMode]. Depending on the implementation, [mode] might be
   * [PreviewMode.Transitive] before being set to [mode].
   */
  fun setMode(newMode: PreviewMode.Settable)

  /** Sets the mode to the previous mode, if any. */
  fun restorePrevious()

  companion object {
    val KEY = DataKey.create<PreviewModeManager>("PreviewModeManager")
  }
}

/**
 * A class that represents a Preview Mode. Each [PreviewMode] stores data that is specific to a
 * Preview Mode. There are two main types of [PreviewMode]:
 * * [PreviewMode.Settable] which represents a "final" mode and is a type of mode that can be set
 *   through the [PreviewModeManager].
 * * [PreviewMode.Transitive] which represents a transitory mode when going from one
 *   [PreviewMode.Settable] to another.
 */
sealed class PreviewMode {

  /** Type if [LayoutMode] to be used with this [PreviewMode]. */
  open val layoutMode: LayoutMode = LayoutMode.Default

  /** Background color. */
  open val backgroundColor: Color = Colors.DEFAULT_BACKGROUND_COLOR

  sealed class Transitive : PreviewMode()

  open class Settable : PreviewMode() {}

  object Default : Settable()

  sealed class Focus<T : PreviewElement>(val selected: T) : Settable()

  class UiCheck(
    selected: PreviewElement,
    val atfChecksEnabled: Boolean = StudioFlags.NELE_ATF_FOR_COMPOSE.get(),
    val visualLintingEnabled: Boolean = StudioFlags.NELE_COMPOSE_VISUAL_LINT_RUN.get()
  ) : Focus<PreviewElement>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
  }
  // TODO(b/290579083): extract Essential mode outside of PreviewMode
  class Gallery(selected: PreviewElement) : Focus<PreviewElement>(selected) {
    override val layoutMode: LayoutMode = LayoutMode.Gallery
  }

  class Interactive(selected: PreviewElement) : Focus<PreviewElement>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
  }

  class AnimationInspection(selected: PreviewElement) : Focus<PreviewElement>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
  }

  /**
   * The preview is currently transitioning from [currentMode] to [newMode]. If a state needs a
   * start-up process that might take a while, this mode will be used while the switch is happening.
   * This is the case for example for [Interactive] where the transition from and to the state might
   * take some time.
   */
  data class Switching(val currentMode: Settable, val newMode: Settable) : Transitive()
}
