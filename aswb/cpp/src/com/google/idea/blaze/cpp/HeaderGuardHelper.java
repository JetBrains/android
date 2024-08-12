/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.preprocessor.OCHeaderGuardDetector;
import com.jetbrains.cidr.lang.preprocessor.OCHeaderGuardInfo;
import com.jetbrains.cidr.lang.psi.OCDirective;
import org.jetbrains.annotations.Nullable;

/** Helper methods for header guard. */
public final class HeaderGuardHelper {
  private HeaderGuardHelper() {}

  @Nullable
  public static OCDirective findBeginIfndefHeaderGuardDirective(
      PsiFile file, boolean maybeWithoutLastEndif) {
    OCHeaderGuardInfo headerGuardInfo =
        OCHeaderGuardDetector.findHeaderGuard(file, maybeWithoutLastEndif);
    if (headerGuardInfo == null) {
      return null;
    }
    return headerGuardInfo.getBeginIfndefDirective();
  }
}
