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
package com.android.tools.idea.editors.hprof.views;

import com.android.ddmlib.BitmapDecoder;
import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.memoryanalyzer.HprofBitmapProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import icons.ImagesIcons;
import org.intellij.images.editor.impl.ImageEditorManagerImpl;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class ViewBitmapAction extends AnAction {
  public ViewBitmapAction() {
    super(AndroidBundle.message("android.profiler.hprof.actions.view.bitmap.title"),
          AndroidBundle.message("android.profiler.hprof.actions.view.bitmap.description"), ImagesIcons.ImagesFileType);
  }

  @Override
  public void update(AnActionEvent e) {
    ClassInstance selectedClassInstance = e.getData(InstancesTreeView.SELECTED_CLASS_INSTANCE);
    Presentation presentation = e.getPresentation();
    if (selectedClassInstance != null) {
      boolean isBitmap = HprofBitmapProvider.canGetBitmapFromInstance(selectedClassInstance);
      if (isBitmap) {
        presentation.setEnabled(true);
        return;
      }
    }
    presentation.setEnabled(false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ClassInstance selectedClassInstance = e.getData(InstancesTreeView.SELECTED_CLASS_INSTANCE);
    if (selectedClassInstance == null) {
      return;
    }
    try {
      BufferedImage img = BitmapDecoder.getBitmap(new HprofBitmapProvider(selectedClassInstance));

      final JComponent comp;
      if (img != null) {
        comp = ImageEditorManagerImpl.createImageEditorUI(img);
      }
      else {
        String errorMessage = AndroidBundle.message("android.profiler.hprof.actions.view.bitmap.fail");
        comp = new JLabel(errorMessage, Messages.getErrorIcon(), SwingConstants.CENTER);
      }
      Project project = e.getData(CommonDataKeys.PROJECT);
      JBPopup popup = DebuggerUIUtil.createValuePopup(project, comp, null);
      JFrame frame = WindowManager.getInstance().getFrame(project);
      Dimension frameSize = frame.getSize();
      Dimension size = new Dimension(frameSize.width / 2, frameSize.height / 2);
      popup.setSize(size);
      popup.show(new RelativePoint(frame, new Point(size.width / 2, size.height / 2)));
    }
    catch (Exception ignored) {
    }
  }
}
