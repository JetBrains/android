/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class ExternalBuildFileNode extends PsiFileNode {
  @NotNull private final String myModuleName;

  ExternalBuildFileNode(@NotNull Project project,
                        @NotNull PsiFile value,
                        @NotNull ViewSettings settings,
                        @NotNull String moduleName) {
    super(project, value, settings);
    myModuleName = moduleName;
  }

  @Override
  public void update(@NotNull PresentationData data) {
    super.update(data);
    String fileName = getFileName();
    data.addText(fileName, REGULAR_ATTRIBUTES);
    data.setPresentableText(fileName);
    data.addText(" (" + myModuleName + ", " + getFilePath() + ")", GRAY_ATTRIBUTES);
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return myModuleName + "-" + getFileName() + "-" + getFilePath();
  }

  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getFileName() + " (" + myModuleName + ", " + getFilePath() + ")";
  }

  @NotNull
  private String getFileName() {
    return getFile().getName();
  }

  @NotNull
  private String getFilePath() {
    return getLocationRelativeToUserHome(getFile().getVirtualFile().getPresentableUrl());
  }

  @NotNull
  private PsiFile getFile() {
    PsiFile value = getValue();
    assert value != null;
    return value;
  }
}