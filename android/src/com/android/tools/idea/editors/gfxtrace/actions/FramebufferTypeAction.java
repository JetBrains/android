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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class FramebufferTypeAction extends PopupAction {
  @NotNull private static final Action[] MRT_SUPPORTED_ACTIONS = {
    new Action("Color Buffer 0", "Display the first color framebuffer", AndroidIcons.GfxTrace.ColorBuffer0, FramebufferAttachment.Color0),
    new Action("Color Buffer 1", "Display the second color framebuffer", AndroidIcons.GfxTrace.ColorBuffer1, FramebufferAttachment.Color1),
    new Action("Color Buffer 2", "Display the third color framebuffer", AndroidIcons.GfxTrace.ColorBuffer2, FramebufferAttachment.Color2),
    new Action("Color Buffer 3", "Display the fourth color framebuffer", AndroidIcons.GfxTrace.ColorBuffer3, FramebufferAttachment.Color3),
    new Action("Depth Buffer", "Display the depth framebuffer", AndroidIcons.GfxTrace.DepthBuffer, FramebufferAttachment.Depth)
  };
  @NotNull private static final Action[] MRT_UNSUPPORTED_ACTIONS = {
    new Action("Color Buffer", "Display the color framebuffer", AndroidIcons.GfxTrace.ColorBuffer, FramebufferAttachment.Color0),
    new Action("Depth Buffer", "Display the depth framebuffer", AndroidIcons.GfxTrace.DepthBuffer, FramebufferAttachment.Depth)
  };

  @NotNull private final FrameBufferController myFrameBufferController;
  private Action myAction = MRT_UNSUPPORTED_ACTIONS[0];
  private boolean myMultiRenderTargetSupport = false;
  private ActionToolbar myToolbar;

  public FramebufferTypeAction(@NotNull FrameBufferController controller) {
    super("Select Render Target", "Select the render target to display", AndroidIcons.GfxTrace.ColorBuffer);
    myFrameBufferController = controller;
  }

  public void setMultiRenderTargetSupport(boolean multiRenderTargetSupport) {
    myMultiRenderTargetSupport = multiRenderTargetSupport;
    myToolbar = null;
    myAction = (myMultiRenderTargetSupport ? MRT_SUPPORTED_ACTIONS : MRT_UNSUPPORTED_ACTIONS)[0];
    getToolbar().updateActionsImmediately();
  }

  private ActionToolbar getToolbar() {
    if (myToolbar == null) {
      DefaultActionGroup actions = new DefaultActionGroup();
      for (final Action action : myMultiRenderTargetSupport ? MRT_SUPPORTED_ACTIONS : MRT_UNSUPPORTED_ACTIONS) {
        actions.add(new ToggleAction(action.text, action.description, action.icon) {
          @Override
          public boolean isSelected(AnActionEvent e) {
            return myAction.attachment == action.attachment;
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            myAction = action;
            myFrameBufferController.setFramebufferAttachment(action.attachment);
            getToolbar().updateActionsImmediately();
          }
        });
      }
      myToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
      myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    }
    return myToolbar;
  }

  @Override
  protected JComponent getPopupContents(AnActionEvent e) {
    return getToolbar().getComponent();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setIcon(myAction.icon);
  }

  private static class Action {
    public final String text;
    public final String description;
    public final Icon icon;
    public final FramebufferAttachment attachment;

    public Action(String text, String description, Icon icon, FramebufferAttachment attachment) {
      this.text = text;
      this.description = description;
      this.icon = icon;
      this.attachment = attachment;
    }
  }
}
