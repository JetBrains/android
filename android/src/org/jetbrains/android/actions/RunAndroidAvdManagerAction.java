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
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * @author Eugene.Kudelevsky
 */
public class RunAndroidAvdManagerAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.RunAndroidAvdManagerAction");
  private AvdListDialog myDialog;

  public RunAndroidAvdManagerAction() {
    super(getName());
  }

  public RunAndroidAvdManagerAction(String name) {
    super(name);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    e.getPresentation().setEnabled(
      project != null &&
      !ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).isEmpty() &&
      AndroidSdkUtils.isAndroidSdkAvailable());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    openAvdManager(e.getProject());
  }

  public void openAvdManager(@Nullable Project project) {
    if (myDialog != null && !myDialog.isDisposed()) {
      myDialog.getFrame().toFront();
    } else {
      myDialog = new AvdListDialog(project);
      myDialog.init();
      myDialog.show();
    }
  }

  @Nullable
  public AvdInfo getSelected() {
    return myDialog.getSelected();
  }

  public static String getName() {
    return AndroidBundle.message("android.run.avd.manager.action.text");
  }
}
