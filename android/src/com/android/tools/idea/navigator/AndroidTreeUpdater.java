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
package com.android.tools.idea.navigator;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

public class AndroidTreeUpdater extends AbstractTreeUpdater {
  private final AbstractTreeStructure myTreeStructure;
  private final AndroidProjectTreeBuilder myTreeBuilder;

  public AndroidTreeUpdater(@NotNull AbstractTreeStructure treeStructure,
                            @NotNull AndroidProjectTreeBuilder treeBuilder) {
    super(treeBuilder);
    myTreeStructure = treeStructure;
    myTreeBuilder = treeBuilder;
  }

  /**
   * Modified version of {@link com.intellij.ide.projectView.impl.ProjectViewPane.ProjectViewTreeUpdater}
   * This makes sure that the hierarchy of nodes is refreshed properly when for example a resource file is added or deleted.
   */
  @Override
  public boolean addSubtreeToUpdateByElement(Object element) {
    if (element instanceof PsiDirectory && !myTreeBuilder.getProject().isDisposed()) {
      PsiDirectory dirToUpdateFrom = (PsiDirectory)element;
      boolean addedOk;
      while (!(addedOk = super.addSubtreeToUpdateByElement(dirToUpdateFrom == null? myTreeStructure.getRootElement() : dirToUpdateFrom))) {
        if (dirToUpdateFrom == null) {
          break;
        }
        dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
      }
      return addedOk;
    }

    return super.addSubtreeToUpdateByElement(element);
  }
}
