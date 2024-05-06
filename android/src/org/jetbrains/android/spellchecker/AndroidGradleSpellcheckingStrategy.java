/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.spellchecker;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.spellchecker.GroovySpellcheckingStrategy;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

public class AndroidGradleSpellcheckingStrategy extends GroovySpellcheckingStrategy {
  @Override
  public boolean isMyContext(@NotNull PsiElement element) {
    return isInGradleFile(element);
  }

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    // Only allow print and println strings to be tokenized. We don't want to flag
    // strings in Gradle files that are not under the user's control: names of
    // dependencies, names of plugins, names of class path elements, etc. Turns
    // out there are a lot of different forms of these, so rather than trying to
    // list all the types of strings we don't want to spell check, instead we
    // simply allow "print" and "println":
    if (TokenSets.STRING_LITERAL_SET.contains(element.getNode().getElementType()) &&
        !isPrint(element)) {
      // If this element references some external name, such as a dependency or an
      // application package, there is no point in flagging it since the user can't
      // change it
      return EMPTY_TOKENIZER;
    }

    return super.getTokenizer(element);
  }

  private static boolean isPrint(PsiElement element) {
    PsiElement parent0 = element.getParent();
    if (parent0 == null) {
      return false;
    }
    PsiElement parent1 = parent0.getParent();
    if (parent1 == null) {
      return false;
    }
    PsiElement parent2 = parent1.getParent();
    if (parent2 == null) {
      return false;
    }

    if (parent2 instanceof GrCommandArgumentList) {
      parent2 = parent2.getParent();
    }

    if (parent2 instanceof GrApplicationStatement) {
      GrApplicationStatement call = (GrApplicationStatement)parent2;

      GrExpression propertyExpression = call.getInvokedExpression();
      if (propertyExpression instanceof GrReferenceExpression) {
        GrReferenceExpression propertyRef = (GrReferenceExpression)propertyExpression;
        String property = propertyRef.getReferenceName();
        if ("print".equals(property) || "println".equals(property)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isInGradleFile(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      String name = file.getName();
      if (endsWithIgnoreCase(name, DOT_GRADLE)) {
        return true;
      }
    }

    return false;
  }
}
