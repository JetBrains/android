/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang;

import com.google.common.collect.ImmutableList;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/**
 * Runs first, delegating to whichever {@link FindUsagesHandler} would have run. This class is used
 * to ignore some factory that does not work with aswb for now.
 *
 * <p>TODO(b/372646344, b/370573555): Remove the whole class after fixing
 */
public class ASwBFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  public static final BoolExperiment ignoreCmakeFindUsageFactoryEnabled =
      new BoolExperiment("ignore.cmake.findusage.enabled", true);
  public static ImmutableList<String> IGNORE_FACTORIES =
      ImmutableList.of("com.jetbrains.cmake.search.usages.CMakeUsageHandlerFactory");

  @Override
  public boolean canFindUsages(PsiElement element) {
    return ignoreCmakeFindUsageFactoryEnabled.getValue();
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(PsiElement element, boolean forHighlightUsages) {
    if (forHighlightUsages) {
      return null;
    }
    return getDelegateHandler(element, forHighlightUsages);
  }

  @Nullable
  private static FindUsagesHandler getDelegateHandler(
      PsiElement element, boolean forHighlightUsages) {
    for (FindUsagesHandlerFactory factory :
        Extensions.getExtensions(FindUsagesHandlerFactory.EP_NAME, element.getProject())) {
      if (factory instanceof ASwBFindUsagesHandlerFactory
          || !factory.canFindUsages(element)
          || IGNORE_FACTORIES.contains(factory.getClass().getName())) {
        continue;
      }
      FindUsagesHandler handler = factory.createFindUsagesHandler(element, forHighlightUsages);
      if (handler != null) {
        return handler;
      }
    }
    return null;
  }
}
