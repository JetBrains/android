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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.jarFinder.FindJarFix;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;

/**
 * Class to unload unwanted quick fixes
 */
public class AndroidUnresolvedReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement reference, @NotNull QuickFixActionRegistrar registrar) {
    Module contextModule = findModuleForPsiElement(reference);
    if (contextModule == null) {
      return;
    }
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(contextModule);
    if (gradleFacet == null) {
      return;
    }

    // Since this is a gradle android project, we need to unregister:
    //  "add jar from web quick fix",
    // since those quick fixes would make the iml file and the gradle file out of sync.
    registrar.unregister(new Condition<IntentionAction>() {
      @Override
      public boolean value(IntentionAction intentionAction) {
        return intentionAction instanceof FindJarFix;
      }
    });
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}
