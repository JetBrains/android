/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdListDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class RunAndroidAvdManagerAction extends DumbAwareAction {
  @Nullable private AvdListDialog myDialog;

  public RunAndroidAvdManagerAction() {
    super(getName());
  }

  public RunAndroidAvdManagerAction(@Nullable String name) {
    super(name);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (SystemInfo.isChromeOS) {
      presentation.setVisible(false);
      return;
    }

    presentation.setEnabled(AndroidSdkUtils.isAndroidSdkAvailable());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    openAvdManager(e.getProject());
  }

  public void openAvdManager(@Nullable Project project) {
    if (SystemInfo.isChromeOS) {
      return;
    }

    if (myDialog == null) {
      myDialog = new AvdListDialog(project);
      myDialog.init();
      myDialog.show();
      // Remove the dialog reference when the dialog is disposed (closed).
      Disposer.register(myDialog, () -> myDialog = null);
    }
    else {
      myDialog.getFrame().toFront();
    }
  }

  @Nullable
  public AvdInfo getSelected() {
    return myDialog == null ? null : myDialog.getSelected();
  }

  @NotNull
  public static String getName() {
    return AndroidBundle.message("android.run.avd.manager.action.text");
  }
}
