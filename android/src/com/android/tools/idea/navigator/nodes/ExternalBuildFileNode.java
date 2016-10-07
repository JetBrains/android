/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;

public class ExternalBuildFileNode extends PsiFileNode {
  @NotNull private final String myModuleName;

  public ExternalBuildFileNode(@NotNull Project project,
                               @NotNull PsiFile value,
                               @NotNull ViewSettings viewSettings,
                               @NotNull String moduleName) {
    super(project, value, viewSettings);
    myModuleName = moduleName;
  }

  @Override
  public void update(PresentationData data) {
    super.update(data);
    String fileName = getPsiFileName();
    data.addText(fileName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setPresentableText(fileName);
    data.addText(" (" + myModuleName + ", " + getPsiFilePath() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return myModuleName + "-" + getPsiFileName() + "-" + getPsiFilePath();
  }

  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getPsiFileName() + " (" + myModuleName + ", " + getPsiFilePath() + ")";
  }

  @NotNull
  private PsiFile getPsiFile() {
    PsiFile value = getValue();
    assert value != null;
    return value;
  }

  private String getPsiFileName() {
    return getPsiFile().getName();
  }

  private String getPsiFilePath() {
    return getLocationRelativeToUserHome(getPsiFile().getVirtualFile().getPresentableUrl());
  }
}