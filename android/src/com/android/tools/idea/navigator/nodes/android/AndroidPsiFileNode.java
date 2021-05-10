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
package com.android.tools.idea.navigator.nodes.android;

import static com.android.SdkConstants.FD_MAIN;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidPsiFileNode extends PsiFileNode {
  @Nullable private final String mySourceSetName;

  public AndroidPsiFileNode(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull ViewSettings settings,
                     @Nullable String sourceSetName) {
    super(project, file, settings);
    mySourceSetName = sourceSetName;
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    if (mySourceSetName != null && !FD_MAIN.equals(mySourceSetName)) {
      data.addText(data.getPresentableText(), REGULAR_ATTRIBUTES);
      data.addText(" (" + mySourceSetName + ")", GRAY_ATTRIBUTES);
    }
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    String sourceProviderName = mySourceSetName == null ? "" : mySourceSetName;
    return getQualifiedNameSortKey() + "-" + (FD_MAIN.equals(sourceProviderName) ? "" : sourceProviderName);
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    PsiFile file = getValue();
    assert file != null;
    return AndroidPsiDirectoryNode.toTestString(file.getName(), mySourceSetName);
  }
}
