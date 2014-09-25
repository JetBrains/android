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
package com.android.tools.idea.navigator.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

public class AndroidBuildScriptNode extends PsiFileNode {
  @Nullable private final String myQualifier;

  public AndroidBuildScriptNode(Project project,
                                PsiFile value,
                                ViewSettings viewSettings,
                                @Nullable String qualifier) {
    super(project, value, viewSettings);
    myQualifier = qualifier;
  }

  @Override
  public void update(PresentationData data) {
    super.update(data);

    PsiFile psiFile = getValue();
    if (psiFile == null || !psiFile.isValid()) {
      return;
    }

    String fileName = psiFile.getName();
    data.addText(fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setPresentableText(fileName);
    if (myQualifier != null) {
      data.addText(" (" + myQualifier + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    String priority;

    // We want the build scripts to be ordered as follows:
    //   1. We want the build scripts inside modules.
    //   2. Within a module, we want all the build scripts grouped together
    //   3. Finally, we want all the global and project wide build scripts.
    // This is achieved in a very simple way by the priorities set below.
    if (myQualifier != null) {
      boolean isModuleName = ModuleManager.getInstance(myProject).findModuleByName(myQualifier) != null;
      if (isModuleName) {
        priority = "1-";
      }
      else {
        priority = "2-";
      }
      priority += myQualifier + "-";
    }
    else {
      priority = "3-";
    }

    PsiFile f = getValue();
    return f == null ? priority : priority + f.getName();
  }

  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    String fileName = getValue().getName();
    return fileName + (myQualifier == null ? "" : " (" + myQualifier + ")");
  }
}