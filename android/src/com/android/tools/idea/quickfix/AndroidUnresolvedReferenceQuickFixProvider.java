/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

public class AndroidUnresolvedReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {

  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement reference, @NotNull QuickFixActionRegistrar registrar) {
    final Module contextModule = findModuleForPsiElement(reference);
    if (contextModule == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(contextModule);
    if (facet == null) {
      return;
    }
    final PsiFile contextFile = reference.getContainingFile();
    if (contextFile == null) {
      return;
    }

    // Since this is a gradle android project, we need to unregister:
    //  "add module dependency fix",
    //  "add junit to module quick fix",
    //  "all add library to module quick fix",
    // since those quick fixes would make the iml file and the gradle file out of sync.
    registrar.unregister(new Condition<IntentionAction>() {
      @Override
      public boolean value(IntentionAction intentionAction) {
        return intentionAction instanceof OrderEntryFix;
      }
    });

    // Currently our API doesn't address the case that gradle.build file does not exist at the module folder, so just skip for now.
    if (getGradleBuildFile(contextModule) != null) {
      return;
    }

    // TODO implement a quickfix that could properly "add junit dependency", "add library dependency" and "add module dependency"
    // to the gradle file.
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
