/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.view.nodes;

import com.android.tools.idea.profiling.capture.Capture;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

public class CaptureNode extends SimpleNode {
  @NotNull private final Capture myCapture;

  public CaptureNode(@NotNull Project project, @NotNull Capture capture) {
    super(project);
    myCapture = capture;

    getTemplatePresentation().addText(myCapture.getDescription(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    setIcon(myCapture.getType().getIcon());
  }

  @Override
  public SimpleNode[] getChildren() {
    return new SimpleNode[0];
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }

  @NotNull
  public Capture getCapture() {
    return myCapture;
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    assert myProject != null;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, myCapture.getFile());

    try {
      FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
    }
    catch (IllegalArgumentException ignored) {
      // The file might not be present anymore if the user deletes it from outside the editor.
      // Just ignore this issue, as the editor will soon pick up that the file is gone.
    }
  }

  @Override
  protected void doUpdate() {
    getTemplatePresentation().clearText();
    getTemplatePresentation().addText(myCapture.getDescription(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }
}
