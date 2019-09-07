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
package com.android.tools.idea.uibuilder.handlers.motion.property2.action;

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.InspectorLineModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubSectionControlAction extends AnAction {
  private final NelePropertyItem myProperty;
  private InspectorLineModel myLineModel;

  public SubSectionControlAction(@Nullable NelePropertyItem property) {
    myProperty = property;
  }

  public void setLineModel(@NotNull InspectorLineModel lineModel) {
    myLineModel = lineModel;
    myLineModel.setEnabled(isPresent());
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean isPresent = isPresent();
    Presentation presentation = event.getPresentation();
    presentation.setIcon(isPresent ? AllIcons.Diff.GutterCheckBoxSelected : AllIcons.Diff.GutterCheckBox);
    if (myLineModel != null) {
      myLineModel.setEnabled(isPresent);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (myProperty == null) {
      return;
    }
    MotionSceneTag tag = MotionLayoutAttributesModel.getMotionTag(myProperty);
    String subSection = MotionLayoutAttributesModel.getConstraintSection(myProperty);
    boolean isPresent = tag != null && subSection != null && tag.getChildTags(subSection).length > 0;
    if (tag == null || subSection == null) {
      return;
    }
    if (isPresent) {
      XmlTag xmlTag = tag.getXmlTag();
      if (xmlTag == null) {
        return;
      }
      Project project = myProperty.getProject();
      TransactionGuard.submitTransaction(project, () -> WriteCommandAction.runWriteCommandAction(
        project,
        "Remove " + subSection,
        null,
        () -> Arrays.stream(xmlTag.getSubTags())
          .filter(sub -> sub.getLocalName().equals(subSection))
          .forEach(sub -> sub.delete()),
        xmlTag.getContainingFile()));
    }
    else {
      MotionLayoutAttributesModel.createConstraintTag(tag, subSection, myProperty, false, null);
    }
  }

  private boolean isPresent() {
    if (myProperty == null) {
      return false;
    }
    MotionSceneTag tag = MotionLayoutAttributesModel.getMotionTag(myProperty);
    String subSection = MotionLayoutAttributesModel.getConstraintSection(myProperty);
    return tag != null && (subSection == null || tag.getChildTags(subSection).length > 0);
  }
}
