/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.Processor;
import com.jetbrains.cidr.lang.OCFileTypeHelpers;
import com.jetbrains.cidr.lang.OCIncludeHelper;
import com.jetbrains.cidr.lang.OCIncludeHelpers.ShowInCompletion;
import com.jetbrains.cidr.lang.psi.OCFile;

/**
 * Suppress autocomplete of #include paths showing .cc files, if they don't have configurations:
 * https://youtrack.jetbrains.com/issue/CPP-12762. New issue after 2017.3.
 */
public class BlazeIncludeHelper implements OCIncludeHelper {

  @Override
  public ShowInCompletion showInCompletion(PsiFileSystemItem item) {
    if (item instanceof OCFile) {
      // Just use filename, instead of presence of OCResolveConfigurations. Library files do not
      // have OCResolveConfigurations.
      if (!OCFileTypeHelpers.isHeaderFile(item.getName())) {
        return ShowInCompletion.DON_NOT_SHOW; // I hope they don't fix this typo.
      }
    }
    return ShowInCompletion.DEFAULT;
  }

  @Override
  public boolean processContainingFramework(
      Project project, VirtualFile virtualFile, Processor<PsiFileSystemItem> processor) {
    return true;
  }
}
