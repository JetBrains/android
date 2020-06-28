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

import static com.android.tools.idea.navigator.nodes.ndk.includes.utils.PresentationDataWrapperKt.createPresentationDataWrapper;
import static java.util.Collections.emptyList;

import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.ClassifiedIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageFamilyValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.PresentationDataWrapper;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base view class over {@link ClassifiedIncludeValue}.
 *
 * @param <T> The concrete type of {@link ClassifiedIncludeValue}
 */
public abstract class IncludeViewNode<T extends ClassifiedIncludeValue> extends ProjectViewNode<T> implements FolderGroupNode {
  private String myDescription;
  private int myHashCode;
  protected final Collection<File> myIncludeFolders;
  protected final boolean myShowPackageType;

  protected IncludeViewNode(@NotNull T thisInclude,
                            Collection<File> allIncludes,
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
                                                  Collection<File> allIncludes,
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
  public List<PsiDirectory> getFolders() {
    return emptyList();
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    T value = getValue();
    return "[icon-f]" + (value == null ? "" : value.getSortKey());
  }

  @NotNull
  @Override
  final public String toString() {
    return myDescription;
  }

  private void lazyInitializeHashCodeAndDescription() {
    if (myDescription == null) {
      StringBuilder sb = new StringBuilder();
      writeDescription(createPresentationDataWrapper(sb));
      myDescription = sb.toString();
      myHashCode = Objects.hash(myDescription);
    }
  }

  /**
   * The purpose of this function is to write the description of the node in the project tree. So this text as well as the icons and
   * other UI stuff that goes along with it:
   *
   * <pre>
   *  app
   *    cpp
   *      includes
   *        NDK Components
   * </pre>
   *
   * <p>
   * In addition, this same text uniquely identifies the node inside the context of the Android.mk or CMakeLists.txt so it is used in
   * the hashCode function.
   */
  abstract void writeDescription(@NotNull PresentationDataWrapper presentation);

  @Override
  final protected void update(@NotNull PresentationData presentation) {
    writeDescription(createPresentationDataWrapper(presentation));
  }

  @Override
  final public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    if (object.getClass() != getClass()) {
      return false;
    }
    IncludeViewNode that = (IncludeViewNode)object;
    lazyInitializeHashCodeAndDescription();
    return Objects.equals(this.myDescription, that.myDescription);
  }

  @Override
  final public int hashCode() {
    lazyInitializeHashCodeAndDescription();
    return myHashCode;
  }
}
