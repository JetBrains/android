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
package com.android.tools.idea.gradle.highlight;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.quickfix.GradleIncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement;
import static com.intellij.util.ReflectionUtil.getField;

public class GradleHighlightQuickfixReplacementVisitor extends JavaElementVisitor implements HighlightVisitor {
  private HighlightInfoHolder myHolder = null;

  @Override
  public void visit(@NotNull PsiElement element) {
    if (myHolder == null) return;
    Module contextModule = findModuleForPsiElement(element);
    if (contextModule == null) {
      return;
    }

    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(contextModule);
    if (gradleFacet == null) {
      return;
    }

    GradleBuildFile gradleBuildFile = GradleBuildFile.get(contextModule);
    // gradleBuildFile could be null here (e.g. subproject is defined in root project's build.gradle file), but we still need to unload
    // the original quickfixes since it is a gradle project

    for (int i = 0; i < myHolder.size(); i++) {
      HighlightInfo info = myHolder.get(i);

      final LanguageLevel[] targetLanguageLevel = {null};

      try {
        info.unregisterQuickFix(new Condition<IntentionAction>() {
          @Override
          public boolean value(IntentionAction intentionAction) {
            if (intentionAction.getClass() == IncreaseLanguageLevelFix.class) {
              // TODO add accessor for IncreaseLanguageLevelFix.myLevel
              targetLanguageLevel[0] = getField(IncreaseLanguageLevelFix.class, intentionAction, LanguageLevel.class, "myLevel");
              if (targetLanguageLevel[0] != null) return true;
            }
            return false;
          }
        });
      }
      catch (NullPointerException e) {
        // ignore, unregisterQuickFix doesn't have null check
      }
      if (targetLanguageLevel[0] == null) {
        continue;
      }
      if (targetLanguageLevel[0].isAtLeast(LanguageLevel.JDK_1_8)) {
        // We don't support Java 8 yet.
        continue;
      }

      if (gradleBuildFile == null) {
        // Currently our API doesn't address the case that gradle.build file does not exist at the module folder, so just skip for now.
        continue;
      }

      // Notice we don't need special handling for "try with resources" feature.
      // Though unlike other syntactic sugar in Java 7, try with resources depends on the actual Java 7 library, which is not officially
      // supported after Android SDK 21, so we can't offer a quickfix to increase the language level to 7.
      // But if we are not using later SDK, IDEA will first show type mismatch error instead of syntax error because it requires
      // the resource defined to be 'AutoClosable' type.

      QuickFixAction.registerQuickFixAction(info, new GradleIncreaseLanguageLevelFix(targetLanguageLevel[0], gradleBuildFile));
      // TODO when we can't increase the language level, maybe we should change the highlight text to reflect that.
    }
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
    myHolder = holder;
    try {
      action.run();
    }
    finally {
      myHolder = null;
    }
    return true;
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return true;
  }

  @NotNull
  @Override
  public GradleHighlightQuickfixReplacementVisitor clone() {
    return new GradleHighlightQuickfixReplacementVisitor();
  }

  @Override
  public int order() {
    return 0;
  }
}
