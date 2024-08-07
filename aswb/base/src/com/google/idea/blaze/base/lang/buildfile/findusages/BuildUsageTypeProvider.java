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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.impl.rules.UsageTypeProviderEx;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Used to provide user-visible string when grouping 'find usages' results by type. */
public class BuildUsageTypeProvider implements UsageTypeProviderEx {
  private static final UsageType IN_LOAD = new UsageType("Usage in load statement");
  private static final UsageType IN_GLOB = new UsageType("Usage in BUILD glob");
  private static final UsageType GENERIC = new UsageType("Usage in BUILD/Skylark file");

  @Override
  @Nullable
  public UsageType getUsageType(PsiElement element) {
    return getUsageType(element, UsageTarget.EMPTY_ARRAY);
  }

  @Override
  @Nullable
  public UsageType getUsageType(PsiElement element, @NotNull UsageTarget[] targets) {
    if (!(element instanceof BuildElement)) {
      return null;
    }
    if (PsiTreeUtil.getParentOfType(element, LoadStatement.class) != null) {
      return IN_LOAD;
    }
    if (PsiTreeUtil.getParentOfType(element, GlobExpression.class, false) != null) {
      return IN_GLOB;
    }
    return GENERIC;
  }
}
