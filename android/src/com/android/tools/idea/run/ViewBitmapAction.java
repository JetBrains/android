/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * {@link ViewBitmapAction} provides the ability to view an android Bitmap object. When this action
 * is invoked, the bitmap is downloaded from the target and displayed in a pop-up.
 */
public class ViewBitmapAction extends DebuggerAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext actionContext = e.getDataContext();
    final DebuggerTreeNodeImpl node = getSelectedNode(actionContext);
    if (node == null) {
      return;
    }

    final Value bitmapValue = getValue(node);
    if (bitmapValue == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(actionContext);
    if (project == null) {
      return;
    }

    final DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    if(debuggerManager == null) {
      return;
    }

    final DebuggerContextImpl debuggerContext = debuggerManager.getContext();
    if (debuggerContext == null || debuggerContext.getDebuggerSession() == null) {
      return;
    }

    final ProgressWindowWithNotification progressWindow = new ProgressWindowWithNotification(true, project);
    final String variableName = node.getText() == null ? "Bitmap" : node.getText().toString();
    DownloadBitmapCommand.CompletionCallback onComplete = new DownloadBitmapCommand.CompletionCallback() {
      @Override
      public void bitmapDownloaded(@NotNull final BufferedImage image) {
        DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
          @Override
          public void run() {
            JComponent imageComponent = new JComponent() {
              @Override
              protected void paintComponent(Graphics g) {
                g.drawImage(image, 0, 0, null);
              }
            };
            JBPopup popup = JBPopupFactory.getInstance()
              .createComponentPopupBuilder(imageComponent, null)
              .setProject(project)
              .setTitle(variableName)
              .setResizable(true)
              .setMovable(true)
              .setFocusable(true)
              .setMinSize(new Dimension(image.getWidth(), image.getHeight()))
              .setShowBorder(true)
              .createPopup();
            popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(actionContext));
          }
        });
      }

      @Override
      public void error(@NotNull final String message) {
        DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
          @Override
          public void run() {
            JBPopup popup = JBPopupFactory.getInstance().createMessage(message);
            popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(actionContext));
          }
        });
      }
    };

    DownloadBitmapCommand downloadBitmapCommand =
      new DownloadBitmapCommand(bitmapValue, debuggerContext, onComplete, progressWindow);

    progressWindow.setTitle("Retrieving Bitmap...");
    debuggerContext.getDebugProcess().getManagerThread().startProgress(downloadBitmapCommand, progressWindow);
  }

  // display this action only if the selected node is a Bitmap
  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    DataContext actionContext = e.getDataContext();
    DebuggerTreeNodeImpl node = getSelectedNode(actionContext);
    Value value = getValue(node);

    // TODO: should this be a check on the type or the value?
    if (!(value instanceof ObjectReference)) {
      presentation.setVisible(false);
      return;
    }

    if (!DownloadBitmapCommand.isSupportedBitmap(value)) {
      presentation.setVisible(false);
    } else {
      presentation.setText("View Bitmap");
      presentation.setVisible(true);
      presentation.setEnabled(true);
    }
  }

  @Nullable
  private static Value getValue(@Nullable DebuggerTreeNodeImpl node) {
    if (node == null) {
      return null;
    }

    NodeDescriptorImpl descriptor = node.getDescriptor();
    if (!(descriptor instanceof ValueDescriptor)) {
      return null;
    }

    return ((ValueDescriptor)descriptor).getValue();
  }
}
