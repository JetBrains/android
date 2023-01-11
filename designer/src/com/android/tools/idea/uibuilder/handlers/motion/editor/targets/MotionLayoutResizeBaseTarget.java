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
package com.android.tools.idea.uibuilder.handlers.motion.editor.targets;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.MotionUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for resizing targets in MotionLayout.
 *
 * TODO: refactor with ResizeBaseTarget
 */
public abstract class MotionLayoutResizeBaseTarget extends ResizeBaseTarget {

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public MotionLayoutResizeBaseTarget(@NotNull Type type) {
    super(type);
  }

  //endregion

  protected abstract void updateAttributes(@NotNull NlAttributesHolder attributes,
                                           @AndroidDpCoordinate int x,
                                           @AndroidDpCoordinate int y);

  protected abstract void updateAttributes(@NotNull MTag.TagWriter attributes,
                                           @AndroidDpCoordinate int x,
                                           @AndroidDpCoordinate int y);

  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x,
                        @AndroidDpCoordinate int y,
                        @NotNull List<Target> closestTargets,
                        @NotNull SceneContext ignored) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(myComponent.getNlComponent().getParent());
    if (!motionLayout.isInTransition()) {
      ComponentModification modification = new ComponentModification(component, "Resize " + StringUtil.getShortName(component.getTagName()));
      updateAttributes(modification, x, y);
      modification.apply();
      myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    MotionLayoutComponentHelper motionLayout = MotionLayoutComponentHelper.create(myComponent.getNlComponent().getParent());
    if (!motionLayout.isInTransition()) {
      if (MotionUtils.isInBaseState(motionLayout)) {
        NlComponent component = myComponent.getAuthoritativeNlComponent();
        ComponentModification modification = new ComponentModification(component, "Resize " + StringUtil.getShortName(component.getTagName()));
        updateAttributes(modification, x, y);
        modification.commit();
      } else {
        String state = motionLayout.getState();
        MTag motionScene = MotionUtils.getMotionScene(myComponent.getNlComponent());
        MTag[] cSet = motionScene.getChildTags("ConstraintSet");
        for (int i = 0; i < cSet.length; i++) {
          MTag set = cSet[i];
          String id = set.getAttributeValue("id");
          id = Utils.stripID(id);
          if (id.equalsIgnoreCase(state)) {
              MTag[] constraints = set.getChildTags(MotionSceneAttrs.Tags.CONSTRAINT);
              for (int j = 0; j < constraints.length; j++) {
                MTag constraint = constraints[j];
                String constraintId = constraint.getAttributeValue("id");
                constraintId = Utils.stripID(constraintId);
                if (constraintId.equalsIgnoreCase(Utils.stripID(myComponent.getId()))) {
                  MTag.TagWriter writer = constraint.getTagWriter();
                  updateAttributes(writer, x, y);
                  writer.commit("Resize component");
                }
              }
            break;
          }
        }
      }
      myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  /**
   * Reset the size and position when mouse resizing is canceled.
   */
  @Override
  public void mouseCancel() {

    // rollback the transaction. The value may be temporarily changed by live rendering.

    super.mouseCancel();
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
