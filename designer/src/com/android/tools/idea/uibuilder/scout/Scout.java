/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scout;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.ConstraintComponentUtilities;

import java.util.ArrayList;
import java.util.List;

/**
 * Main entry for the Scout Inference engine.
 * All external access should be through this class
 * TODO support Stash / merge constraints table etc.
 */
public class Scout {

  public enum Arrange {
    AlignVerticallyTop, AlignVerticallyMiddle, AlignVerticallyBottom, AlignHorizontallyLeft,
    AlignHorizontallyCenter, AlignHorizontallyRight, DistributeVertically,
    DistributeHorizontally, VerticalPack, HorizontalPack, ExpandVertically, AlignBaseline,
    ExpandHorizontally, CenterHorizontallyInParent, CenterVerticallyInParent, CenterVertically,
    CenterHorizontally
  }

  private static int sMargin = 8;

  public static int getMargin() {
    return sMargin;
  }

  public static void setMargin(int margin) {
    sMargin = margin;
  }

  public static void arrangeWidgets(Arrange type, List<NlComponent> widgets,
                                    boolean applyConstraint) {
    ScoutArrange.align(type, widgets, applyConstraint);
  }

  public static void inferConstraints(List<NlComponent> components) {
    NlComponent root = null;
    for (NlComponent component : components) {
      if (component.getParent() == null) {
        inferConstraints(root);
      }
    }
  }

  public static void inferConstraints(NlComponent root) {
    if (root == null) {
      return;
    }
    if (!ConstraintComponentUtilities.isConstraintLayout(root)) {
      return;
    }
    for (NlComponent constraintWidget : root.getChildren()) {
      if (ConstraintComponentUtilities.isConstraintLayout(constraintWidget)) {
        if (!constraintWidget.getChildren().isEmpty()) {
          inferConstraints(constraintWidget);
        }
      }
    }

    ArrayList<NlComponent> list = new ArrayList<>(root.getChildren());
    list.add(0, root);

    NlComponent[] widgets = list.toArray(new NlComponent[list.size()]);
    ScoutWidget.computeConstraints(ScoutWidget.create(widgets));
  }
}
