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

import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.android.SdkConstants;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidPsiDirectoryNode extends PsiDirectoryNode {
  @Nullable private final String mySourceSetName;
  @Nullable private final PsiDirectory myRootSourceFolder;

  public AndroidPsiDirectoryNode(@NotNull Project project,
                          @NotNull PsiDirectory directory,
                          @NotNull ViewSettings settings,
                          @Nullable String sourceSetName,
                          @Nullable PsiDirectory rootSourceFolder) {
    super(project, directory, settings);
    mySourceSetName = sourceSetName;
    myRootSourceFolder = rootSourceFolder;
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    if (mySourceSetName != null && !SdkConstants.FD_MAIN.equals(mySourceSetName)) {
      data.addText(data.getPresentableText(), REGULAR_ATTRIBUTES);
      data.addText(" (" + mySourceSetName + ")", GRAY_ATTRIBUTES);
    }
  }

  @Override
  public boolean canRepresent(Object element) {
    if (super.canRepresent(element)) return true;
    Project project = getProject();
    if (myRootSourceFolder == null || project == null) return false;
    VirtualFile file = tempGetVirtualFile(element);
    PsiDirectory directory = getValue();
    if (file == null || directory == null) return false;
    return ProjectViewDirectoryHelper.getInstance(getProject())
      .canRepresent(file, directory, myRootSourceFolder, getSettings());
  }

  /**
   * Copy from PsiDirectoryNode, delete when not needed.
   */
  @Nullable
  private static VirtualFile tempGetVirtualFile(Object element) {
    if (element instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)element;
      return directory.getVirtualFile();
    }
    return element instanceof VirtualFile ? (VirtualFile)element : null;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    VirtualFile virtualFile = getValue() != null ? getValue().getVirtualFile() : null;
    String path = virtualFile != null ? virtualFile.getPath() : "";
    String sourceProviderName = mySourceSetName == null ? "" : mySourceSetName;
    return getQualifiedNameSortKey() + "-" + (SdkConstants.FD_MAIN.equals(sourceProviderName) ? "" : sourceProviderName) + "-" + path;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    PsiDirectory value = getValue();
    assert value != null;
    return toTestString(value.getName(), mySourceSetName);
  }

  @NotNull
  static String toTestString(@NotNull String element, @Nullable String providerName) {
    StringBuilder buffer = new StringBuilder(element);
    if (providerName != null) {
      buffer.append(" (");
      buffer.append(providerName);
      buffer.append(")");
    }
    return buffer.toString();
  }
}
