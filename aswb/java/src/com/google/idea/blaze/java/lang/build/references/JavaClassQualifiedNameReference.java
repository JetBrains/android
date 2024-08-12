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
package com.google.idea.blaze.java.lang.build.references;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument.Keyword;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.references.AttributeSpecificStringLiteralReferenceProvider;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PatternConditionPlus;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.PairProcessor;
import com.intellij.util.ProcessingContext;

/** Handles attribute values which should be fully-qualified java class names. */
public class JavaClassQualifiedNameReference
    implements AttributeSpecificStringLiteralReferenceProvider {

  private static final ImmutableSet<String> JAVA_CLASS_STRING_TYPES =
      ImmutableSet.of("main_class", "test_class");

  public static final ElementPattern<StringLiteral> PATTERN =
      psiElement(StringLiteral.class)
          .withLanguage(BuildFileLanguage.INSTANCE)
          .inside(
              psiElement(Keyword.class)
                  .with(nameCondition(StandardPatterns.string().oneOf(JAVA_CLASS_STRING_TYPES))));

  private static PatternCondition<PsiElementBase> nameCondition(final ElementPattern<?> pattern) {
    return new PatternConditionPlus<PsiElementBase, String>("_withPsiName", pattern) {
      @Override
      public boolean processValues(
          PsiElementBase t,
          ProcessingContext context,
          PairProcessor<? super String, ? super ProcessingContext> processor) {
        return processor.process(t.getName(), context);
      }
    };
  }

  @Override
  public PsiReference[] getReferences(String attributeName, StringLiteral literal) {
    if (!JAVA_CLASS_STRING_TYPES.contains(attributeName)) {
      return PsiReference.EMPTY_ARRAY;
    }
    return ReferenceProvidersRegistry.getReferencesFromProviders(literal);
  }
}
