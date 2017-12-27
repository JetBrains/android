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

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
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

import static com.android.SdkConstants.FD_MAIN;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.PsiDirectory.EMPTY_ARRAY;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class AndroidManifestFileNode extends PsiFileNode implements FolderGroupNode {
  @NotNull private final AndroidFacet myAndroidFacet;

  AndroidManifestFileNode(@NotNull Project project,
                          @NotNull PsiFile psiFile,
                          @NotNull ViewSettings settings,
                          @NotNull AndroidFacet androidFacet) {
    super(project, psiFile, settings);
    myAndroidFacet = androidFacet;
  }

  @Override
  public void update(@NotNull PresentationData data) {
    super.update(data);

    PsiFile file = getValue();
    // could be null if the file was deleted.
    if (file != null) {
      // if it is not part of the main source set, then append the provider name
      IdeaSourceProvider sourceProvider = getSourceProvider(myAndroidFacet, file);
      if (sourceProvider != null && !FD_MAIN.equals(sourceProvider.getName())) {
        data.addText(file.getName(), REGULAR_ATTRIBUTES);
        String name = sourceProvider.getName();
        if (isNotEmpty(name)) {
          data.addText(" (" + name + ")", GRAY_ATTRIBUTES);
        }
        data.setPresentableText(file.getName());
      }
    }
  }

  @Nullable
  static IdeaSourceProvider getSourceProvider(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    for (IdeaSourceProvider provider : AndroidProjectViewPane.getSourceProviders(facet)) {
      if (file.getVirtualFile().equals(provider.getManifestFile())) {
        return provider;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    PsiFile file = getValue();
    if (file == null) {
      return "";
    }

    IdeaSourceProvider sourceProvider = getSourceProvider(myAndroidFacet, file);
    if (sourceProvider == null || FD_MAIN.equals(sourceProvider.getName())) {
      return "";
    }
    else {
      return sourceProvider.getName();
    }
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @NotNull
  public PsiDirectory[] getFolders() {
    return EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    PsiFile file = getValue();
    if (file == null) {
      return "";
    }
    StringBuilder buffer = new StringBuilder();
    buffer.append(file.getName());
    IdeaSourceProvider sourceProvider = getSourceProvider(myAndroidFacet, file);
    assert sourceProvider != null;
    String name = sourceProvider.getName();
    if (isNotEmpty(name)) {
      buffer.append(" (");
      buffer.append(name);
      buffer.append(")");
    }
    return buffer.toString();
  }
}
