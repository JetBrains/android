/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.navigation;

import com.google.idea.blaze.cpp.PartnerFilePatterns;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.lang.psi.OCFile;
import java.io.File;
import javax.annotation.Nullable;

class SwitchToHeaderOrSourceSearch {

  private SwitchToHeaderOrSourceSearch() {}

  @Nullable
  static OCFile getCorrespondingFile(OCFile file) {
    OCFile target = file.getAssociatedFileWithSameName();
    if (target == null && !file.isHeader() && file.getVirtualFile() != null) {
      target = correlateTestToHeader(file);
    }
    return target;
  }

  @Nullable
  private static OCFile correlateTestToHeader(OCFile file) {
    // Quickly check foo_test.cc -> foo.h as well. "getAssociatedFileWithSameName" only does
    // foo.cc <-> foo.h. However, if you do goto-related-symbol again, it will go from
    // foo.h -> foo.cc instead of back to foo_test.cc.
    PsiManager psiManager = PsiManager.getInstance(file.getProject());
    String pathWithoutExtension = FileUtil.getNameWithoutExtension(file.getVirtualFile().getPath());
    for (String testSuffix : PartnerFilePatterns.DEFAULT_PARTNER_SUFFIXES) {
      if (pathWithoutExtension.endsWith(testSuffix)) {
        String possibleHeaderName = StringUtil.trimEnd(pathWithoutExtension, testSuffix) + ".h";
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(new File(possibleHeaderName), false);
        if (virtualFile != null) {
          PsiFile psiFile = psiManager.findFile(virtualFile);
          if (psiFile instanceof OCFile) {
            return (OCFile) psiFile;
          }
        }
      }
    }
    return null;
  }
}
