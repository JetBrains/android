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
package com.android.tools.idea.uibuilder.handlers.motion.property.action;

import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutAttributesModel;
import com.android.tools.idea.uibuilder.handlers.motion.property.MotionSelection;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.property.panel.api.InspectorLineModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubSectionControlAction extends AnAction {
  private final NlPropertyItem myProperty;
  private InspectorLineModel myLineModel;
  private LookupResult myLookupResult;

  public SubSectionControlAction(@Nullable NlPropertyItem property) {
    myProperty = property;
    myLookupResult = new LookupResult();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    // Updated in the EDT because of the access to properties in check.
    return ActionUpdateThread.EDT;
  }

  public void setLineModel(@NotNull InspectorLineModel lineModel) {
    myLineModel = lineModel;
    myLineModel.setEnabled(check());
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean isPresent = check();
    Presentation presentation = event.getPresentation();
    presentation.setDescription(getCommandName(isPresent));
    presentation.setIcon(isPresent ? AllIcons.Diff.GutterCheckBoxSelected : AllIcons.Diff.GutterCheckBox);
    if (myLineModel != null) {
      myLineModel.setEnabled(isPresent);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    boolean isPresent = check();
    String commandName = getCommandName(isPresent);
    if (commandName == null) {
      return;
    }
    if (isPresent) {
      MTag.TagWriter tagWriter = myLookupResult.subTag.getTagWriter();
      tagWriter.deleteTag();
      tagWriter.commit(commandName);
    }
    else {
      MTag.TagWriter tagWriter = MotionLayoutAttributesModel.createSubTag(myLookupResult.selection,
                                                                          myLookupResult.tag,
                                                                          myLookupResult.subTagName);
      tagWriter.commit(commandName);
    }
  }

  private boolean check() {
    if (myProperty == null) {
      return false;
    }
    MotionSelection selection = MotionLayoutAttributesModel.getMotionSelection(myProperty);
    String subTagName = MotionLayoutAttributesModel.getSubTag(myProperty);
    if (selection == null || subTagName == null ||
        (selection.getType() != MotionEditorSelector.Type.CONSTRAINT &&
         selection.getType() != MotionEditorSelector.Type.TRANSITION)) {
      return false;
    }
    MotionSceneTag tag = selection.getMotionSceneTag();
    if (tag == null) {
      return false;
    }
    MotionSceneTag subTag = MotionLayoutAttributesModel.getSubTag(tag, subTagName);
    myLookupResult.selection = selection;
    myLookupResult.tag = tag;
    myLookupResult.subTagName = subTagName;
    myLookupResult.subTag = subTag;

    return subTag != null;
  }

  @Nullable
  private String getCommandName(boolean isPresent) {
    String subTagName = myLookupResult.subTagName;
    if (subTagName == null) {
      return null;
    }
    if (!isPresent) {
      return String.format("Create %1$s tag", subTagName);
    }
    else {
      return String.format("Remove %1$s tag", subTagName);
    }
  }

  private static class LookupResult {
    MotionSelection selection;
    MotionSceneTag tag;
    String subTagName;
    MotionSceneTag subTag;
  }
}
