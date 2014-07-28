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

import com.android.SdkConstants;
import com.android.resources.ResourceConstants;
import com.google.common.base.Joiner;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidResFileNode extends PsiFileNode {
  public AndroidResFileNode(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull ViewSettings settings) {
    super(project, psiFile, settings);
  }

  @Override
  public void update(PresentationData data) {
    super.update(data);

    String text = data.getPresentableText();
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setPresentableText(text);

    String qualifier = getQualifier(getValue());
    if (qualifier != null) {
      data.addText(qualifier, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    PsiFile psiFile = getValue();
    String qualifier = getQualifier(psiFile);
    return psiFile.getName() + (qualifier == null ? "" : qualifier);
  }

  @Nullable
  private static String getQualifier(@NotNull PsiFile resFile) {
    PsiDirectory resTypeFolder = resFile.getParent();
    if (resTypeFolder == null) { // cannot happen
      return null;
    }

    String folderName = resTypeFolder.getName();
    int index = folderName.indexOf(ResourceConstants.RES_QUALIFIER_SEP);
    String qualifier = index < 0 ? null : folderName.substring(index + 1);

    String providerName = null;
    PsiDirectory resFolder = resTypeFolder.getParent();
    if (resFolder != null) {
      IdeaSourceProvider ideaSourceProvider = resFolder.getUserData(AndroidSourceTypeNode.SOURCE_PROVIDER);
      if (ideaSourceProvider != null) {
        providerName = ideaSourceProvider.getName();
        if (SdkConstants.FD_MAIN.equals(providerName)) {
          providerName = null;
        }
      }
    }

    if (qualifier == null && providerName == null) {
      return null;
    }

    StringBuilder sb = new StringBuilder(10);
    sb.append(" (");
    sb.append(Joiner.on(", ").skipNulls().join(qualifier, providerName));
    sb.append(')');
    return sb.toString();
  }
}
