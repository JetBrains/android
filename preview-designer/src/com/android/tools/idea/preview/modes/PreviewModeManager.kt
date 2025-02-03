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

import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.preview.Colors
import com.android.tools.preview.PreviewElement
import com.google.common.base.Objects
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import java.awt.Color
import kotlinx.coroutines.flow.StateFlow

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
   * (interactive, animation, UI check). Both [PreviewMode.Default] and [PreviewMode.Focus] are
   * normal modes.
   */
  val isNormal: Boolean
    get() = this is Default || this is Focus

  /** Background color. */
  open val backgroundColor: Color = Colors.DEFAULT_BACKGROUND_COLOR

  open val layoutOption: SurfaceLayoutOption = DEFAULT_LAYOUT_OPTION

  open val selected: PreviewElement<*>? = null

  /**
   * This function returns to false if the given [PreviewMode] doesn't trigger any resize of
   * [DesignSurface] (default). Override it to true if are entering a [PreviewMode] that triggers a
   * [DesignSurface] resize explaining why a resize is expected when it returns true.
   *
   * @param previousMode The previous [PreviewMode] before the following one.
   * @param project The [Project] used by the [Preview]
   *
   * Example: entering UiCheck mode from Default mode
   *
   *   Default mode with            Ui Check **resizes** DesignSurface
   *   problem panel close          to show problem panel
   *    _______________             _______________
   *  |        |      |            |        |      |
   *  |        |      |            |________|______|
   *  |        |      |    =>      |               |
   *  |        |      |            | problem panel |
   *  |________|______|            |_______________|
   *
   *
   *   Default mode with          Ui Check **doesn't resize**, problem
   *   problem panel open         panel is already open.
   *   _______________            _______________
   *  |        |      |          |        |      |
   *  |________|______|    =>    |________|______|
   *  |               |          |               |
   *  | problem panel |          | problem panel |
   *  |_______________|          |_______________|
   *
   *  Example: entering Ui Check from focus Mode
   *
   *   Focus mode with              Ui Check **resizes** DesignSurface
   *   problem panel close          to delete Focus mode tab bar and
   *                                create problem panel
   *    _______________             _______________
   *  |        |______|            |        |      |
   *  |        | O  O |            |________|______|
   *  |        | o  O |    =>      |               |
   *  |        | o  O |            | problem panel |
   *  |________|______|            |_______________|
   *
   *   Focus mode with            Ui Check **resizes** DesignSurface
   *   problem panel open         to delete Focus mode tab bar and
   *     _______________           _______________
   *   |        |______|          |        |      |
   *   |        | O  O |    =>    |________|______|
   *   |        | o  O |          |               |
   *   |        | o  O |          | problem panel |
   *   |________|______|          |_______________|
   *
   */
  open fun expectResizeOnEnter(previousMode: PreviewMode?, project: Project): Boolean = false

  /**
   * Returns a [PreviewMode] with the same content as the current one, but with a different layout
   * option if that is allowed by the mode. Modes that want to react to layout changes have to
   * override this.
   */
  open fun deriveWithLayout(layoutOption: SurfaceLayoutOption): PreviewMode {
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

  class Default(override val layoutOption: SurfaceLayoutOption = DEFAULT_LAYOUT_OPTION) :
    RestorePreviewMode() {

    // Resize is expected in Default PreviewMode if the previous Preview was Animation Inspection or
    // Focus mode.
    override fun expectResizeOnEnter(previousMode: PreviewMode?, project: Project): Boolean {
      return previousMode is Focus || previousMode is AnimationInspection
    }

    override fun deriveWithLayout(layoutOption: SurfaceLayoutOption): PreviewMode {
      return Default(layoutOption)
    }
  }

  sealed class SingleItemMode<T : PreviewElement<*>>(override val selected: T) : PreviewMode()

  /** Represents a mode that can be restored when clicking on "Stop" when inside a mode. */
  sealed class RestorePreviewMode : PreviewMode()

  class UiCheck(
    val baseInstance: UiCheckInstance,
    override val layoutOption: SurfaceLayoutOption = UI_CHECK_LAYOUT_OPTION,
  ) : PreviewMode() {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR

    override fun expectResizeOnEnter(previousMode: PreviewMode?, project: Project): Boolean {
      if (previousMode is Focus) {
        // We always expect a resize on enter if the previous mode was of type Focus.
        return true
      }
      val isProblemPanelNotVisible =
        ProblemsViewToolWindowUtils.getToolWindow(project)?.isVisible == false
      // If we are in Default mode and the problem panel is not open we expect a resize when
      // entering Ui Check mode.
      return previousMode is Default && isProblemPanelNotVisible
    }

    override fun deriveWithLayout(layoutOption: SurfaceLayoutOption): PreviewMode {
      return UiCheck(baseInstance, layoutOption)
    }

    override fun equals(other: Any?): Boolean {
      return super.equals(other) && baseInstance == (other as UiCheck).baseInstance
    }

    override fun hashCode(): Int {
      return Objects.hashCode(super.hashCode(), baseInstance)
    }
  }

  class Focus(override val selected: PreviewElement<*>?) : RestorePreviewMode() {
    override val layoutOption: SurfaceLayoutOption = FOCUS_MODE_LAYOUT_OPTION

    // We always return true because the Focus PreviewMode resizes DesignSurface to create the top
    // FocusModeTabs toolbar.
    override fun expectResizeOnEnter(previousMode: PreviewMode?, project: Project) = true

    /**
     * If list of previews is updated while [PreviewMode.Focus] is selected - [selected] element
     * might become invalid and new [Focus] mode with new corresponding [selected] element should be
     * created. At the moment there is no exact match which preview element is which after update.
     * So we are doing our best guess to select new element.
     */
    fun newMode(
      newElements: Collection<PreviewElement<*>>,
      previousElements: Set<PreviewElement<*>>,
    ): Focus {
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

      return Focus(newSelected)
    }

    /**
     * Checks if the [otherMode] is [PreviewMode.Focus] and if their [PreviewMode.Focus.selected]
     * tabs are different.
     *
     * @param otherMode the [PreviewMode] that we want to compare with this [PreviewMode.Focus].
     * @return true if the [otherMode] is [PreviewMode.Focus] and if their
     *   [PreviewMode.Focus.selected] tabs are different, return false otherwise.
     */
    fun isFocusModeWithDifferentTabs(otherMode: PreviewMode): Boolean =
      otherMode is Focus && this.selected != otherMode.selected
  }

  class Interactive(selected: PreviewElement<*>) : SingleItemMode<PreviewElement<*>>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
    override val layoutOption = GRID_NO_GROUP_LAYOUT_OPTION

    // We expect a resize if the previous mode was of type Focus.
    override fun expectResizeOnEnter(previousMode: PreviewMode?, project: Project): Boolean {
      return previousMode is Focus
    }
  }

  class AnimationInspection(selected: PreviewElement<*>) :
    SingleItemMode<PreviewElement<*>>(selected) {
    override val backgroundColor: Color = Colors.ACTIVE_BACKGROUND_COLOR
    override val layoutOption = GRID_NO_GROUP_LAYOUT_OPTION

    // We always return true when entering Animation Inspection PreviewMode because of the animation
    // panel positioned below the preview.
    override fun expectResizeOnEnter(previousMode: PreviewMode?, project: Project) = true
  }
}

/**
 * Characteristic information of a UI Check instance
 *
 * @param baseElement The preview element from which the UI Check mode is launched
 * @param isWearPreview Whether the preview element is a Wear preview
 */
data class UiCheckInstance(val baseElement: PreviewElement<*>, val isWearPreview: Boolean)
