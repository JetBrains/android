/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.uibuilder.actions.ChainStyleViewAction.ChainDirection
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction
import com.android.tools.idea.uibuilder.api.actions.ViewAction
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ChainChecker
import com.google.common.annotations.VisibleForTesting
import icons.StudioIcons.LayoutEditor.Toolbar
import javax.swing.Icon

class ChainStyleViewActions {
  companion object {
    @JvmField
    val HORIZONTAL_CHAIN_STYLES: List<ViewAction> = listOf(
      ChainStyleViewAction(
        Toolbar.CYCLE_CHAIN_SPREAD,
        SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD,
        ChainDirection.HORIZONTAL),
      ChainStyleViewAction(
        Toolbar.CYCLE_CHAIN_SPREAD_INLINE,
        SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE,
        ChainDirection.HORIZONTAL,
        "spread inside"),
      ChainStyleViewAction(
        Toolbar.CYCLE_CHAIN_PACKED,
        SdkConstants.ATTR_LAYOUT_CHAIN_PACKED,
        ChainDirection.HORIZONTAL))

    @JvmField
    val VERTICAL_CHAIN_STYLES: List<ViewAction> = listOf(
      ChainStyleViewAction(
        Toolbar.CYCLE_CHAIN_SPREAD,
        SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD,
        ChainDirection.VERTICAL),
      ChainStyleViewAction(
        Toolbar.CYCLE_CHAIN_SPREAD_INLINE,
        SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE,
        ChainDirection.VERTICAL,
        "spread inside"),
      ChainStyleViewAction(
        Toolbar.CYCLE_CHAIN_PACKED,
        SdkConstants.ATTR_LAYOUT_CHAIN_PACKED,
        ChainDirection.VERTICAL))
  }
}

/**
 * View action for each chain styles available in ConstraintLayout.
 *
 * @param style one of [SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD],
 *    [SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE] or [SdkConstants.ATTR_LAYOUT_CHAIN_PACKED].
 * @param chainDirection direction of the chain.
 * @param label Display string visible to users. Note that IntelliJ menu removes underscore ("_").
 */
@VisibleForTesting
class ChainStyleViewAction(
  icon: Icon?,
  val style: String,
  private val chainDirection: ChainDirection,
  label: String = style) : DirectViewAction(icon, label) {

  @VisibleForTesting
  enum class ChainDirection {
    HORIZONTAL,
    VERTICAL
  }

  override fun perform(editor: ViewEditor,
                       handler: ViewHandler,
                       component: NlComponent,
                       selectedChildren: MutableList<NlComponent>,
                       modifiers: Int) {
    if (selectedChildren.isEmpty()) {
      return
    }

    val primaryNlComponent = selectedChildren[0]
    val primary = editor.scene.getSceneComponent(primaryNlComponent) ?: return
    val nonPrimaryComponents = getNonPrimaryComponent(editor, primaryNlComponent, selectedChildren)

    if (chainDirection == ChainDirection.HORIZONTAL) {
      val horizontalHead = getHorizontalHead(primary, nonPrimaryComponents) ?: return
      chooseChainStyle(horizontalHead, SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, primary, style)
    } else if (chainDirection == ChainDirection.VERTICAL) {
      val verticalHead = getVerticalHead(primary, nonPrimaryComponents) ?: return
      chooseChainStyle(verticalHead, SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, primary, style)
    }
  }

  /**
   * Select the chain style, and commit the changes to the backend (e.g. XmlTag).
   */
  private fun chooseChainStyle(chainHeadComponent: SceneComponent,
                               orientationStyle: String,
                               component: SceneComponent,
                               chainStyle: String) {
    val chainHead = chainHeadComponent.authoritativeNlComponent
    val modification = ComponentModification(chainHead, "Cycle Chain Style")
    modification.setAttribute(SdkConstants.SHERPA_URI, orientationStyle, chainStyle)
    modification.commit()
    component.scene.needsRebuildList()
  }

  /**
   * Precondition: [chainDirection] == [ChainDirection.HORIZONTAL]
   * Checks all the non primary components to ensure that they're on the same direction (horizontal), then returns the chain head.
   */
  private fun getHorizontalHead(primary: SceneComponent, nonPrimaryComponents: List<SceneComponent>): SceneComponent? {
    val checker = ChainChecker()
    if (!checker.checkIsInChain(primary) || !checker.isInHorizontalChain) {
      return null
    }

    val head = checker.horizontalChainHead
    nonPrimaryComponents.forEach {
      val componentChecker = ChainChecker()
      componentChecker.checkIsInChain(it)
      if (!componentChecker.isInHorizontalChain || componentChecker.horizontalChainHead !== head) {
        return null
      }
    }
    return head
  }

  /**
   * Precondition: [chainDirection] == [ChainDirection.VERTICAL]
   * Checks all the non primary components to ensure that they're on the same direction (vertical), then returns the chain head.
   */
  private fun getVerticalHead(primary: SceneComponent, nonPrimaryComponents: List<SceneComponent>): SceneComponent? {
    val checker = ChainChecker()
    if (!checker.checkIsInChain(primary) || !checker.isInVerticalChain) {
      return null
    }

    val head = checker.verticalChainHead
    nonPrimaryComponents.forEach {
      val componentChecker = ChainChecker()
      componentChecker.checkIsInChain(it)
      if (!componentChecker.isInVerticalChain || componentChecker.verticalChainHead !== head) {
        return null
      }
    }
    return head
  }

  private fun getNonPrimaryComponent(
    editor: ViewEditor,
    primaryNlComponent: NlComponent,
    selectedChildren: List<NlComponent>)
    : List<SceneComponent> {
    return selectedChildren
      .filter { it: NlComponent -> it !== primaryNlComponent }
      .map { it: NlComponent? -> editor.scene.getSceneComponent(it) }
      .toList().filterNotNull()
  }

  override fun updatePresentation(presentation: ViewActionPresentation,
                                  editor: ViewEditor,
                                  handler: ViewHandler,
                                  component: NlComponent,
                                  selectedChildren: MutableList<NlComponent>,
                                  modifiersEx: Int) {
    super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiersEx)
    presentation.setVisible(isApplicable(editor, selectedChildren))
  }

  @VisibleForTesting
  fun isApplicable(editor: ViewEditor, selectedChildren: List<NlComponent>): Boolean {
    if (selectedChildren.isEmpty()) {
      return false
    }

    val primaryNlComponent = selectedChildren[0]
    val primary = editor.scene.getSceneComponent(primaryNlComponent) ?: return false
    val nonPrimaryComponents = getNonPrimaryComponent(editor, primaryNlComponent, selectedChildren)

    if (chainDirection == ChainDirection.HORIZONTAL) {
      getHorizontalHead(primary, nonPrimaryComponents) ?: return false
      return true
    } else if (chainDirection == ChainDirection.VERTICAL) {
      getVerticalHead(primary, nonPrimaryComponents) ?: return false
      return true
    }

    return false
  }

}