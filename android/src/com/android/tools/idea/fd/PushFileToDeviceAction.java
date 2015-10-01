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
package com.android.tools.idea.fd;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import java.awt.event.InputEvent;


public class PushFileToDeviceAction extends AnAction {
  public PushFileToDeviceAction() {
    super("Instant Run: Push Changed Files To Running App Instantly", null, AndroidIcons.PushFileToDevice);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (FastDeployManager.DISPLAY_STATISTICS) {
      FastDeployManager.notifyBegin();
    }
    Module module = e.getData(LangDataKeys.MODULE);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (module != null && file != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        Project project = module.getProject();

        // TODO: Check isGradleProject && gradle model compatibility!

        // Save document first
        FileDocumentManager dm = FileDocumentManager.getInstance();
        Document document = dm.getDocument(file);
        if (document != null) {
          dm.saveDocument(document);
        }

        FastDeployManager manager = project.getComponent(FastDeployManager.class);
        boolean forceRestart = (e.getModifiers() & InputEvent.CTRL_MASK) != 0;
        manager.computeDeltas(facet, file, forceRestart ? UpdateMode.COLD_SWAP : UpdateMode.HOT_SWAP);
      }
    }
  }
}
