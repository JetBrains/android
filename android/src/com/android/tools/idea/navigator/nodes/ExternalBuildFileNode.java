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

public class ExternalBuildFileNode extends PsiFileNode {
  public static final String MODULE_PREFIX = "Module: ";

  @NotNull private final String myQualifier;

  public ExternalBuildFileNode(@NotNull Project project,
                               @NotNull PsiFile value,
                               @NotNull ViewSettings viewSettings,
                               @NotNull String qualifier) {
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
    data.addText(" (" + MODULE_PREFIX + myQualifier + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return myQualifier + "-" + getPsiFile().getName();
  }

  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getPsiFile().getName() + " (" + MODULE_PREFIX +  myQualifier + ")";
  }

  @NotNull
  private PsiFile getPsiFile() {
    PsiFile value = getValue();
    assert value != null;
    return value;
  }
}