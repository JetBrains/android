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

import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceConstants;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.google.common.base.Joiner;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class AndroidResFileNode extends PsiFileNode implements Comparable {
  private final AndroidFacet myFacet;

  AndroidResFileNode(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull ViewSettings settings, @NotNull AndroidFacet facet) {
    super(project, psiFile, settings);
    myFacet = facet;
  }

  @Override
  public void update(@NotNull PresentationData data) {
    super.update(data);

    String text = data.getPresentableText();
    data.addText(text, REGULAR_ATTRIBUTES);
    data.setPresentableText(text);

    String qualifier = getQualifier();
    if (qualifier != null) {
      data.addText(qualifier, GRAY_ATTRIBUTES);
    }
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    PsiFile psiFile = getValue();
    assert psiFile != null;
    String qualifier = getQualifier();
    return psiFile.getName() + (qualifier == null ? "" : qualifier);
  }

  @Nullable
  String getQualifier() {
    PsiFile resFile = getValue();
    if (resFile == null) { // happens if psiFile becomes invalid
      return null;
    }

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
      IdeaSourceProvider ideaSourceProvider = findSourceProviderForResFolder(resFolder);
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

    return " (" + Joiner.on(", ").skipNulls().join(qualifier, providerName) + ')';
  }

  @Nullable
  public FolderConfiguration getFolderConfiguration() {
    PsiFile psiFile = getValue();
    if (psiFile == null) { // happens if psiFile becomes invalid
      return null;
    }

    PsiDirectory folder = psiFile.getParent();
    return folder == null ? null : FolderConfiguration.getConfigForFolder(folder.getName());
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return this;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return this;
  }

  @Override
  public int compareTo(@NotNull Object obj) {
    return AndroidResComparator.INSTANCE.compare(this, obj);
  }

  @Nullable
  String getResName() {
    PsiFile f = getValue();
    return (f == null || !f.isValid()) ? null : f.getName();
  }

  @Nullable
  private IdeaSourceProvider findSourceProviderForResFolder(@NotNull PsiDirectory resFolder) {
    for (IdeaSourceProvider provider : AndroidProjectViewPane.getSourceProviders(myFacet)) {
      if (provider.getResDirectories().contains(resFolder.getVirtualFile())) {
        return provider;
      }
    }

    return null;
  }
}
