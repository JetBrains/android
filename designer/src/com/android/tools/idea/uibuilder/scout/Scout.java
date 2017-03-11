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

import com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.ConstraintComponentUtilities;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.uibuilder.actions.ConvertToConstraintLayoutAction.*;

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
    for (NlComponent component : components) {
      if (component.getParent() == null) {
        inferConstraints(component);
        return;
      }
    }
  }

  /**
   * Infer constraints will only set the attributes via a transaction; a separate
   * commit need to be done to save them.
   *
   * @param root the root element to infer from
   */
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

  public static void inferConstraintsAndCommit(List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getParent() == null) {
        inferConstraintsAndCommit(component);
        return;
      }
    }
  }

  /**
   * Infer constraints and do a write commit of the attributes
   *
   * @param component the root element to infer from
   */
  public static void inferConstraintsAndCommit(NlComponent component) {
    inferConstraints(component);
    ArrayList<NlComponent> list = new ArrayList<>(component.getChildren());
    list.add(0, component);
    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();
    String label = "Infering constraints";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        for (NlComponent component : list) {
          AttributesTransaction transaction = component.startAttributeTransaction();
          transaction.commit();
        }
      }
    };
    action.execute();
  }
}
