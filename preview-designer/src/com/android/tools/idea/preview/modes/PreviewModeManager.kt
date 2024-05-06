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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.Colors
import com.android.tools.preview.PreviewElement
import com.google.common.base.Objects
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.StateFlow
import java.awt.Color

/**
 * Interface used for Preview Representations that support [PreviewMode]s. Classes implementing this
 * interface can only be in a single [PreviewMode] at a time, such as [PreviewMode.Default] or
 * [PreviewMode.Interactive].
 */
interface PreviewModeManager {
  /** The current [PreviewMode]. */
  val mode: StateFlow<PreviewMode>

  /** Sets the mode to the previous mode, if any. */
  fun restorePrevious()

  fun setMode(mode: PreviewMode)

  companion object {
    val KEY = DataKey.create<PreviewModeManager>("PreviewModeManager")

    fun areModesOfDifferentType(mode1: PreviewMode?, mode2: PreviewMode?): Boolean {
      // TODO(b/309802158): Find a better way to check whether the new mode is of the same type
      //  as the old one.
      return mode1?.javaClass != mode2?.javaClass
    }
  }
}

/**
 * A class that represents a Preview Mode. Each [PreviewMode] stores data that is specific to a
 * Preview Mode.
 */
sealed class PreviewMode {

  /**
   * Indicates whether the preview is in its default mode by opposition to one of the special modes
   * (interactive, animation, UI check). Both [PreviewMode.Default] and [PreviewMode.Gallery] are
   * normal modes.
   */
  val isNormal: Boolean
    get() = this is Default || this is Gallery

  /** Background color. */
  open val backgroundColor: Color = Colors.DEFAULT_BACKGROUND_COLOR

  open val layoutOption: SurfaceLayoutManagerOption = LIST_LAYOUT_MANAGER_OPTION

  open val selected: PreviewElement? = null

  /**
   * Returns a [PreviewMode] with the same content as the current one, but with a different layout
   * option if that is allowed by the mode. Modes that want to react to layout changes have to
   * override this.
   */
  open fun deriveWithLayout(layoutOption: SurfaceLayoutManagerOption): PreviewMode {
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PreviewMode
    return backgroundColor == other.backgroundColor &&
      layoutOption == other.layoutOption &&
      selected == other.selected
  }

  override fun hashCode(): Int {
    return Objects.hashCode(backgroundColor, layoutOption, selected)
  }

  class Default(
    override val layoutOption: SurfaceLayoutManagerOption = LIST_LAYOUT_MANAGER_OPTION
  ) : RestorePreviewMode() {
    override fun deriveWithLayout(layoutOption: SurfaceLayoutManagerOption): PreviewMode {
      return Default(layoutOption)
    }
  }

  sealed class Focus<T : PreviewElement>(override val selected: T) : PreviewMode()

  /** Represents a mode that can be restored when clicking on "Stop" when inside a mode. */
  sealed class RestorePreviewMode : PreviewMode()

  class UiCheck(
    val baseElement: PreviewElement,
    override val layoutOption: SurfaceLayoutManagerOption = GRID_LAYOUT_MANAGER_OPTIONS,
    val atfChecksEnabled: Boolean = StudioFlags.NELE_ATF_FOR_COMPOSE.get(),
    val visualLintingEnabled: Boolean = StudioFlags.NELE_COMPOSE_VISUAL_LINT_RUN.get()
  ) : PreviewMode() {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR

    override fun deriveWithLayout(layoutOption: SurfaceLayoutManagerOption): PreviewMode {
      return UiCheck(baseElement, layoutOption, atfChecksEnabled, visualLintingEnabled)
    }

    override fun equals(other: Any?): Boolean {
      return super.equals(other) && baseElement == (other as UiCheck).baseElement
    }

    override fun hashCode(): Int {
      return Objects.hashCode(super.hashCode(), baseElement)
    }
  }

  class Gallery(override val selected: PreviewElement?) : RestorePreviewMode() {
    override val layoutOption: SurfaceLayoutManagerOption = PREVIEW_LAYOUT_GALLERY_OPTION

    /**
     * If list of previews is updated while [PreviewMode.Gallery] is selected - [selected] element
     * might become invalid and new [Gallery] mode with new corresponding [selected] element should
     * be created. At the moment there is no exact match which preview element is which after
     * update. So we are doing our best guess to select new element.
     */
    fun newMode(
      newElements: Collection<PreviewElement>,
      previousElements: Set<PreviewElement>,
    ): Gallery {
      // Try to match which element was selected before
      // If selectedKey was removed select first key. If it was only updated (i.e. if a
      // parameter value has changed), we select the new key corresponding to it.

      // That is a trivial case. When the selected key is present, keep the selection.
      if (newElements.contains(selected)) return this

      // Try to guess which exactly element was updated. Select the only element what changed.
      // For example: the element what was selected before was updated. In this case only one
      // element has changed compare to previously available elements. So we are trying to find
      // this updated element.
      val newSelected =
        (newElements subtract previousElements).singleOrNull()
          // We couldn't find any best match. Default to the first key.
          ?: newElements.firstOrNull()

      // TODO(b/292482974): Find the correct key when there are Multipreview changes

      return Gallery(newSelected)
    }
  }

  class Interactive(selected: PreviewElement) : Focus<PreviewElement>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
  }

  class AnimationInspection(selected: PreviewElement) : Focus<PreviewElement>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
  }
}
