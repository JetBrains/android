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
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.ClassifiedIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageFamilyValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base view class over ClassifiedIncludeExpression.
 *
 * @param <T> The concrete type of ClassifiedIncludeExpression
 */
public abstract class IncludeViewNode<T extends ClassifiedIncludeValue> extends ProjectViewNode<T> implements FolderGroupNode {
  @NotNull protected final IncludeSet myIncludeFolders;

  protected final boolean myShowPackageType;

  protected IncludeViewNode(@NotNull T thisInclude,
                            @NotNull IncludeSet allIncludes,
                            boolean showPackageType,
                            @Nullable Project project,
                            @NotNull ViewSettings viewSettings) {
    super(project, thisInclude, viewSettings);
    this.myIncludeFolders = allIncludes;
    this.myShowPackageType = showPackageType;
  }

  /**
   * Construct a concrete IncludeView depending on the type of thisInclude.
   *
   * @param thisInclude     The included expression this view node represents
   * @param allIncludes     All includes in the set of includes in the correct include order
   * @param showPackageType If true, should show the package type in the node
   * @param project         The Android Studio project
   * @param viewSettings    The Android Studio view settings
   * @return the new view node
   */
  public static IncludeViewNode createIncludeView(@NotNull ClassifiedIncludeValue thisInclude,
                                                  @NotNull IncludeSet allIncludes,
                                                  boolean showPackageType,
                                                  @Nullable Project project,
                                                  @NotNull ViewSettings viewSettings) {
    if (thisInclude instanceof SimpleIncludeValue) {
      return new SimpleIncludeViewNode((SimpleIncludeValue)thisInclude, allIncludes, showPackageType, project, viewSettings);
    }
    if (thisInclude instanceof PackageValue) {
      return new PackagingViewNode(allIncludes, project, (PackageValue)thisInclude, viewSettings, showPackageType);
    }
    if (thisInclude instanceof PackageFamilyValue) {
      return new PackagingFamilyViewNode(allIncludes, project, (PackageFamilyValue)thisInclude, viewSettings,
                                         showPackageType);
    }
    throw new RuntimeException(thisInclude.getClass().toString());
  }

  @NotNull
  @Override
  public PsiDirectory[] getFolders() {
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    T value = getValue();
    return "[icon-f]" + (value == null ? "" : value.getSortKey());
  }
}
