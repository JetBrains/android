/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene.targets;

import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.getRegularActionIconRect;
import static com.android.tools.idea.naveditor.scene.NavActionHelperKt.getSelfActionIconRect;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.naveditor.model.ActionType;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.scene.CurvePoints;
import com.android.tools.idea.naveditor.scene.NavActionHelperKt;
import com.google.common.collect.ImmutableList;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An Action in the navigation editor
 */
public class ActionTarget extends BaseTarget {

  private static final Rectangle2D.Float SOURCE_RECT = new Rectangle2D.Float();
  private static final Rectangle2D.Float DEST_RECT = new Rectangle2D.Float();

  private final SceneComponent mySourceComponent;
  private final SceneComponent myDestComponent;

  private final ActionType myActionType;

  public ActionTarget(@NotNull SceneComponent component, @NotNull SceneComponent source, @NotNull SceneComponent destination) {
    setComponent(component);
    mySourceComponent = source;
    myDestComponent = destination;
    myActionType = NavComponentHelperKt.getActionType(component.getNlComponent(), component.getScene().getRoot().getNlComponent());
  }

  // TODO: This should depend on selection
  @Override
  public int getPreferenceLevel() {
    return DRAG_LEVEL;
  }

  @Nullable
  @Override
  public List<SceneComponent> newSelection() {
    return ImmutableList.of(myComponent);
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    // TODO
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    Rectangle2D.Float source = Coordinates.getSwingRectDip(transform, mySourceComponent.fillDrawRect2D(0, SOURCE_RECT));
    boolean isPopAction = NavComponentHelperKt.getPopUpTo(myComponent.getNlComponent()) != null;
    Rectangle2D.Float iconRect = null;

    if (myActionType == ActionType.SELF) {
      @SwingCoordinate Point2D.Float[] points = NavActionHelperKt.selfActionPoints(source, (float)transform.getScale());
      for (int i = 1; i < points.length; i++) {
        picker.addLine(this, 0, (int)points[i - 1].x, (int)points[i - 1].y, (int)points[i].x, (int)points[i].y, 5);
      }

      if (isPopAction) {
        iconRect = getSelfActionIconRect(points[0], (float)transform.getScale());
      }
    }
    else {
      float scale = (float)transform.getScale();
      Rectangle2D.Float dest = Coordinates.getSwingRectDip(transform, myDestComponent.fillDrawRect2D(0, DEST_RECT));
      CurvePoints points = NavActionHelperKt.getCurvePoints(source, dest, scale);
      picker.addCurveTo(this, 0, (int)points.p1.x, (int)points.p1.y, (int)points.p2.x, (int)points.p2.y, (int)points.p3.x, (int)points.p3.y,
                        (int)points.p4.x, (int)points.p4.y, 10);

      if (isPopAction) {
        iconRect = getRegularActionIconRect(source, dest, scale);
      }
    }

    if (iconRect != null) {
      picker.addRect(this, 0, (int)iconRect.x, (int)iconRect.y, (int)(iconRect.x + iconRect.width), (int)(iconRect.y + iconRect.height));
    }
  }

  @Override
  public String getToolTipText() {
    return getComponent().getId();
  }
}

