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
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidManifestFileNode extends PsiFileNode implements DirectoryGroupNode {
  @NotNull private final AndroidFacet myFacet;

  public AndroidManifestFileNode(@NotNull Project project,
                                 @NotNull PsiFile psiFile,
                                 @NotNull ViewSettings settings,
                                 @NotNull AndroidFacet facet) {
    super(project, psiFile, settings);
    myFacet = facet;
  }

  @Override
  public void update(PresentationData data) {
    super.update(data);

    PsiFile file = getValue();

    // could be null if the file was deleted
    if (file == null) {
      return;
    }

    // if it is not part of the main source set, then append the provider name
    IdeaSourceProvider sourceProvider = getSourceProvider(myFacet, file);
    if (sourceProvider != null && !SdkConstants.FD_MAIN.equals(sourceProvider.getName())) {
      data.addText(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      data.addText(" (" + sourceProvider.getName() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      data.setPresentableText(file.getName());
    }
  }

  @Nullable
  public static IdeaSourceProvider getSourceProvider(@NotNull AndroidFacet facet, @NotNull PsiFile file) {
    for (IdeaSourceProvider provider : AndroidProjectViewPane.getSourceProviders(facet)) {
      if (file.getVirtualFile().equals(provider.getManifestFile())) {
        return provider;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    PsiFile file = getValue();
    if (file == null) {
      return "";
    }

    IdeaSourceProvider sourceProvider = getSourceProvider(myFacet, file);
    if (sourceProvider == null || SdkConstants.FD_MAIN.equals(sourceProvider.getName())) {
      return  "";
    } else {
      return sourceProvider.getName();
    }
  }

  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    PsiFile file = getValue();
    if (file == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append(file.getName());
    sb.append(" (");
    sb.append(getSourceProvider(myFacet, getValue()).getName());
    sb.append(")");
    return sb.toString();
  }
}
