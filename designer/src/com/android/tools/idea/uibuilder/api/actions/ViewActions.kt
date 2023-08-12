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

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.actions.ToggleAllShowDecorationsAction
import com.android.tools.idea.uibuilder.actions.ToggleShowTooltipsAction
import com.android.tools.idea.uibuilder.api.ToggleSizeViewAction
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
 * @param myIcon  the icon to be shown if in the toolbar
 * @param myLabel the menu label (if in a context menu) or the tooltip (if in a toolbar)
 */
constructor(private val myIcon: Icon?,
            private val myLabel: String) : ViewAction {

  override fun getLabel(): String = myLabel
  override fun getIcon(): Icon? = myIcon
  override fun affectsUndo(): Boolean = true

  override fun updatePresentation(presentation: ViewActionPresentation,
                                  editor: ViewEditor,
                                  handler: ViewHandler,
                                  component: NlComponent,
                                  selectedChildren: MutableList<NlComponent>,
                                  modifiersEx: Int) {
    presentation.setIcon(icon)
    presentation.setLabel(label)
    presentation.setVisible(true)
  }
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
                                  .add(ToggleAllShowDecorationsAction())
                                  .add(ToggleShowTooltipsAction())
                                  .build()))

  /**
   * Returns the toggle size actions, which contains the [ToggleSizeViewAction] for horizontal and vertical directions.
   */
  @JvmStatic
  fun getToggleSizeActions(): List<ViewAction> {
    val actions = mutableListOf<ViewAction>()
    actions.add(ToggleSizeViewAction("Toggle Width", SdkConstants.ATTR_LAYOUT_WIDTH, StudioIcons.LayoutEditor.Toolbar.EXPAND_HORIZONTAL,
                                     StudioIcons.LayoutEditor.Toolbar.CENTER_HORIZONTAL))
    actions.add(ToggleSizeViewAction("Toggle Height", SdkConstants.ATTR_LAYOUT_HEIGHT, StudioIcons.LayoutEditor.Toolbar.EXPAND_VERTICAL,
                                     StudioIcons.LayoutEditor.Toolbar.CENTER_VERTICAL))
    return actions
  }
}