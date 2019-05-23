/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.api.actions

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.actions.ToggleShowDecorationsAction
import com.android.tools.idea.uibuilder.actions.ToggleShowTooltipsAction
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.google.common.collect.ImmutableList
import icons.StudioIcons
import javax.swing.Icon

/**
 * An abstract view action
 */
abstract class AbstractViewAction
/**
 * Creates a new view action with a given icon and label.
 * By default, this class will make the action visible and set the {@link ViewAction} icon
 * and label into the passed {@link ViewActionPresentation}.
 *
 * @param myRank the relative sorting order of this action; see [.getRank]
 * for details
 * @param myIcon  the icon to be shown if in the toolbar
 * @param myLabel the menu label (if in a context menu) or the tooltip (if in a toolbar)
 */
constructor(protected val myIcon: Icon?,
            protected val myLabel: String) : ViewAction {
  private var myRank: Int = -1

  override fun getRank(): Int = myRank
  override fun getLabel(): String = myLabel
  override fun getIcon(): Icon? = myIcon
  override fun affectsUndo(): Boolean = true

  override fun updatePresentation(presentation: ViewActionPresentation,
                                  editor: ViewEditor,
                                  handler: ViewHandler,
                                  component: NlComponent,
                                  selectedChildren: MutableList<NlComponent>,
                                  modifiers: Int) {
    presentation.setIcon(icon)
    presentation.setLabel(label)
    presentation.setVisible(true)
  }

  fun setRank(rank: Int) {
    // For now the rank for classes implementing AbstractViewAction is mutable. This is to allow instanceof checks on the children.
    // The rank should be removed so this is a temporary solution.
    myRank = rank
  }
}

private class ViewActionWithRank(private val delegate: ViewAction, private val newRank: Int) : ViewAction by delegate {
  override fun getRank(): Int = newRank
}

/**
 * Returns a [ViewAction] with a new rank. This allows overriding the default action rank.
 * @deprecated Do not use ranks. Ordering should be based on list ordering.
 */
fun ViewAction.withRank(rank: Int): ViewAction = if (this is AbstractViewAction) {
  setRank(rank)
  this
}
else {
  ViewActionWithRank(this, rank)
}

object ViewActionUtils {
  /**
   * Returns the "View Options" default menu item. This can be used by handlers that one to customize the
   * option by adding additional actions to it
   */
  @JvmStatic
  @JvmOverloads
  fun getViewOptionsAction(additionalActions: List<ViewAction> = listOf()): ViewAction =
    NestedViewActionMenu("View Options",
                         StudioIcons.Common.VISIBILITY_INLINE,
                         listOf(ImmutableList.builder<ViewAction>()
                                  .addAll(additionalActions)
                                  .add(ToggleShowDecorationsAction())
                                  .add(ToggleShowTooltipsAction())
                                  .build()))

}