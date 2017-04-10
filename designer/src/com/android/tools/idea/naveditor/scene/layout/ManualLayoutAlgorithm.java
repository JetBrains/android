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
package com.android.tools.idea.naveditor.scene.layout;

import org.jetbrains.android.dom.navigation.NavigationSchema;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.TOOLS_URI;

/**
 * {@link NavSceneLayoutAlgorithm} that puts screens in locations specified in the model, falling back to some other method if none is
 * specified.
 */
public class ManualLayoutAlgorithm implements NavSceneLayoutAlgorithm {
  static final String ATTR_X = "manual_x";
  static final String ATTR_Y = "manual_y";
  private final NavSceneLayoutAlgorithm myFallback;
  private final NavigationSchema mySchema;

  public ManualLayoutAlgorithm(@NotNull NavSceneLayoutAlgorithm fallback, @NotNull NavigationSchema schema) {
    myFallback = fallback;
    mySchema = schema;
  }

  @Override
  public void layout(@NotNull SceneComponent component) {
    // TODO: support other destination types
    if (mySchema.getDestinationType(component.getNlComponent().getTagName()) != NavigationSchema.DestinationType.FRAGMENT) {
      return;
    }
    NlComponent nlComponent = component.getNlComponent();
    String xStr = nlComponent.getLiveAttribute(TOOLS_URI, ATTR_X);
    String yStr = nlComponent.getLiveAttribute(TOOLS_URI, ATTR_Y);
    if (xStr != null && yStr != null) {
      @AndroidDpCoordinate int x = ConstraintUtilities.getDpValue(nlComponent, xStr);
      @AndroidDpCoordinate int y = ConstraintUtilities.getDpValue(nlComponent, yStr);
      component.setPosition(x, y);
    }
    else {
      myFallback.layout(component);
    }
  }

  public void save(@NotNull SceneComponent component) {
    NlComponent nlComponent = component.getNlComponent();
    AttributesTransaction transaction = new AttributesTransaction(nlComponent);
    ConstraintComponentUtilities.setDpAttribute(TOOLS_URI, ATTR_X, transaction, component.getDrawX());
    ConstraintComponentUtilities.setDpAttribute(TOOLS_URI, ATTR_Y, transaction, component.getDrawY());
    Project project = nlComponent.getModel().getProject();
    XmlFile file = nlComponent.getModel().getFile();
    WriteCommandAction action = new WriteCommandAction(project, "Save screen location", file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();
  }
}
