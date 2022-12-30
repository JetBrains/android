/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import icons.StudioIcons;
import java.awt.Image;
import javax.swing.Icon;

/**
 * Provide indirection to StudioIcons
 */
public final class MEIcons {
  public static final Icon SLOW_MOTION =  StudioIcons.LayoutEditor.Motion.SLOW_MOTION;
  public static final Icon PLAY =   StudioIcons.LayoutEditor.Motion.PLAY;
  public static final Icon FORWARD = StudioIcons.LayoutEditor.Motion.GO_TO_END;
  public static final Icon BACKWARD = StudioIcons.LayoutEditor.Motion.GO_TO_START;
  public static final Icon LOOP_BACKWARD = StudioIcons.LayoutEditor.Motion.PLAY_BACKWARD;
  public static final Icon LOOP_FORWARD = StudioIcons.LayoutEditor.Motion.PLAY_FORWARD;
  public static final Icon LOOP_YOYO = StudioIcons.LayoutEditor.Motion.PLAY_YOYO;
  public static final Icon PAUSE = StudioIcons.LayoutEditor.Motion.PAUSE;
  public static final Icon LIST_LAYOUT = StudioIcons.LayoutEditor.Motion.BASE_LAYOUT; // TODO fix
  public static final Icon LIST_STATE =  StudioIcons.Common.CHECKED;
  public static final Icon LIST_STATE_DERIVED = Utils.computeLiteIcon(Utils.ICON_LIGHT, MEIcons.LIST_STATE);
  public static final Icon LIST_STATE_DERIVED_SELECTED = Utils.computeLiteIcon(Utils.ICON_LIGHT_SELECTED, MEIcons.LIST_STATE);
  public static final Icon LIST_STATE_SELECTED = Utils.computeLiteIcon(Utils.ICON_SELECTED, MEIcons.LIST_STATE);
  public static final Icon CONSTRAINT_SET = StudioIcons.LayoutEditor.Motion.CONSTRAINT_SET; // TODO fix
  public static final Icon LIST_TRANSITION = StudioIcons.LayoutEditor.Motion.TRANSITION; // TODO fix
  public static final Icon LIST_GRAY_STATE = StudioIcons.LayoutEditor.Toolbar.EXPAND_TO_FIT; // TODO fix
  public static final Icon CREATE_MENU = StudioIcons.LayoutEditor.Toolbar.ADD_COMPONENT; // TODO fix
  public static final Icon CYCLE_LAYOUT =  AllIcons.Actions.SwapPanels; // TODO fix
  public static final Icon EDIT_MENU = StudioIcons.Common.EDIT; // TODO fix
  public static final Icon EDIT_MENU_DISABLED = StudioIcons.Avd.EDIT; // TODO fix
  public static final Icon CREATE_KEYFRAME = StudioIcons.LayoutEditor.Motion.ADD_KEYFRAME;
  public static final Icon GESTURE = StudioIcons.LayoutEditor.Motion.GESTURE;

  public static final Icon CREATE_TRANSITION = StudioIcons.LayoutEditor.Motion.ADD_TRANSITION; // TODO fix
  public static final Icon CREATE_CONSTRAINTSET = StudioIcons.LayoutEditor.Motion.ADD_CONSTRAINT_SET; // TODO fix  ;
  public static final Icon CREATE_ON_STAR = StudioIcons.LayoutEditor.Motion.ADD_GESTURE; // TODO fix

  public static final Icon CREATE_ON_CLICK = StudioIcons.LayoutEditor.Motion.ADD_GESTURE; // TODO fix
  public static final Icon CREATE_ON_SWIPE = StudioIcons.LayoutEditor.Motion.ADD_GESTURE; // TODO fix

  public static final Icon SMALL_DOWN_ARROW = AllIcons.General.ArrowDownSmall;
  public static final Icon SAVE = AllIcons.Actions.MenuSaveall;

  public static final Image getUnscaledIconImage(Icon icon) {
    return IconUtil.toImage(icon, ScaleContext.createIdentity());
  }
}
