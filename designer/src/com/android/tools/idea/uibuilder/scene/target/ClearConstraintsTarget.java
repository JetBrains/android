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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.draw.DrawAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class ClearConstraintsTarget extends ActionTarget implements ActionTarget.Action {

  public ClearConstraintsTarget(ActionTarget previous) {
    super(previous, null);
    setAction(this);
    setActionType(DrawAction.CLEAR);
  }

  @Override
  public void apply(SceneComponent component) {
    AttributesTransaction transaction = component.getNlComponent().startAttributeTransaction();
    clearAllAttributes(transaction);
    transaction.apply();

    NlModel nlModel = component.getNlComponent().getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Cleared all constraints";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();
  }
}
