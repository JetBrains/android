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
package com.android.tools.idea.editors.gfxtrace.actions;

import com.android.tools.idea.editors.gfxtrace.controllers.FrameBufferController;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos.FramebufferAttachment;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FramebufferTypeAction extends ToggleAction {
  @NotNull private final FramebufferAttachment myBufferType;
  @NotNull private final FrameBufferController myFrameBufferController;
  private boolean myIsVisible = true;

  public FramebufferTypeAction(@NotNull FrameBufferController controller,
                               @NotNull FramebufferAttachment bufferType,
                               @Nullable final String text, @Nullable final String description, @Nullable final Icon icon) {
    super(text, description, icon);
    myFrameBufferController = controller;
    myBufferType = bufferType;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myFrameBufferController.getFramebufferAttachment().equals(myBufferType);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myFrameBufferController.setFramebufferAttachment(myBufferType);
  }

  public void setVisible(boolean visible) {
    myIsVisible = visible;
  }

  @Override
  public void update(@NotNull final AnActionEvent e){
    super.update(e);
    e.getPresentation().setVisible(myIsVisible);
  }
}
