/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.projectview.psi;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewFileType;
import com.intellij.psi.tree.IFileElementType;

/** Collects the types used by the PsiBuilder to construct the AST */
public interface ProjectViewElementTypes {

  IFileElementType FILE = new IFileElementType(ProjectViewFileType.INSTANCE.getLanguage());

  ProjectViewElementType LIST_SECTION =
      new ProjectViewElementType("list_section", ProjectViewPsiListSection.class);
  ProjectViewElementType SCALAR_SECTION =
      new ProjectViewElementType("scalar_section", ProjectViewPsiScalarSection.class);

  ProjectViewElementType LIST_ITEM =
      new ProjectViewElementType("list_item", ProjectViewPsiListItem.class);
  ProjectViewElementType SCALAR_ITEM =
      new ProjectViewElementType("scalar_item", ProjectViewPsiScalarItem.class);
}
