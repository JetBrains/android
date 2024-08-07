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
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;

/** PSI file for project view file. */
public class ProjectViewPsiFile extends PsiFileBase {

  public ProjectViewPsiFile(FileViewProvider viewProvider) {
    super(viewProvider, ProjectViewFileType.INSTANCE.getLanguage());
  }

  @Override
  public FileType getFileType() {
    return ProjectViewFileType.INSTANCE;
  }
}
