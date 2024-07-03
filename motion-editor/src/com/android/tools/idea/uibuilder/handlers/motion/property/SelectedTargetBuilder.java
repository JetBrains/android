/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.property.panel.api.InspectorBuilder;
import com.android.tools.property.panel.api.InspectorPanel;
import com.android.tools.property.panel.api.PropertiesTable;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An InspectorBuilder for an informative line (or 2) in the top of the properties panel.
 */
class SelectedTargetBuilder implements InspectorBuilder<NlPropertyItem> {
  private static final String UNNAMED_TARGET = "<unnamed>";

  @Override
  public void attachToInspector(@NotNull InspectorPanel inspector, @NotNull PropertiesTable<NlPropertyItem> properties) {
    NlPropertyItem any = properties.getFirst();
    if (any == null) {
      return;
    }
    MotionSelection selection = MotionLayoutAttributesModel.getMotionSelection(any);
    if (selection == null) {
      return;
    }
    MotionSceneTag motionTag = selection.getMotionSceneTag();
    NlPropertyItem idProperty = properties.getOrNull(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID);

    String label = motionTag != null ? motionTag.getTagName() : "";
    String id = idProperty != null ? idProperty.getValue() : null;
    Icon icon = null;
    boolean includeTopBorder = false;

    switch (selection.getType()) {
      case CONSTRAINT_SET:
        inspector.addComponent(new ConstraintSetPanel(id), null);
        return;

      case CONSTRAINT:
        inspector.addComponent(new ConstraintSetPanel(selection.getConstraintSetId()), null);
        includeTopBorder = true;
        label = MotionSceneAttrs.Tags.CONSTRAINT;
        icon = selection.getComponentIcon();
        if (id == null) {
          id = selection.getComponentId();
        }
        break;

      case TRANSITION:
        if (addTransitionPanel(inspector, motionTag)) {
          return;
        }
        id = "";
        icon = StudioIcons.LayoutEditor.Motion.TRANSITION;
        break;

      case KEY_FRAME:
      case KEY_FRAME_GROUP:
        if (motionTag != null) {
          includeTopBorder = addTransitionPanel(inspector, motionTag.getParent());
        }
        icon = StudioIcons.LayoutEditor.Motion.KEYFRAME;
        id = "";
        break;

      case LAYOUT:
      case LAYOUT_VIEW:
        icon = selection.getComponentIcon();
        break;
    }
    if (id == null) {
      id = UNNAMED_TARGET;
    }
    if (icon == null) {
      icon = StudioIcons.LayoutEditor.Palette.VIEW;
    }

    inspector.addComponent(new SelectedTagPanel(label, Utils.stripID(id), icon, includeTopBorder), null);
  }

  private static boolean addTransitionPanel(@NotNull InspectorPanel inspector, @Nullable MTag motionTag) {
    if (motionTag == null) {
      return false;
    }
    if (MotionSceneAttrs.Tags.KEY_FRAME_SET.equals(motionTag.getTagName())) {
      motionTag = motionTag.getParent();
    }
    if (motionTag == null || !MotionSceneAttrs.Tags.TRANSITION.equals(motionTag.getTagName())) {
      return false;
    }

    String start = Utils.stripID(motionTag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START));
    String end = Utils.stripID(motionTag.getAttributeValue(MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_END));
    inspector.addComponent(new TransitionPanel(start, end), null);
    return true;
  }

  @Override
  public void resetCache() {
  }
}
