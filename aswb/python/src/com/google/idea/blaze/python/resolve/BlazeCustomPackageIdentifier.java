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
package com.google.idea.blaze.python.resolve;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyCustomPackageIdentifier;

/** Bypass the check for __init__.py files. Bazel doesn't require these to be present. */
public class BlazeCustomPackageIdentifier implements PyCustomPackageIdentifier {

  @Override
  public boolean isPackage(PsiDirectory directory) {
    if (!Blaze.isBlazeProject(directory.getProject())) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isPackageFile(PsiFile file) {
    return false;
  }
}
