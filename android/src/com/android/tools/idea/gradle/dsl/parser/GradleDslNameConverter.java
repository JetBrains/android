/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface GradleDslNameConverter {
  /**
   * Converts Psi of an external language into a string suitable as input to GradleNameElement (with dotted notation indicating
   * hierarchy).  Implementors should perform syntactic analysis of the {@code element} as appropriate to the external language.
   *
   * @param element a Psi element containing the external-syntax name
   * @return a string containing a dotted-name representation of the external-named element
   */
  @NotNull
  default String psiToName(@NotNull PsiElement element) { return ""; }

  /**
   * Converts text of an external language into a string suitable as input to GradleNameElement (with dotted notation indicating
   * hierarchy).  Implementors should perform conversion of the {@code element} as appropriate to the external language.
   *
   * @param context the Dsl element in the context of which we are examining this reference
   * @param referenceText the external text denoting a reference
   * @return a string containing a dotted-name representation of the external-named element
   */
  @NotNull
  default String convertReferenceText(@NotNull GradleDslElement context, @NotNull String referenceText) { return ""; }

  /**
   * Converts a dotted-hierarchy name with hierarchy denoted by canonical model names to a dotted-hierarchy name in the vocabulary
   * of the external Dsl language.  Does not perform any syntactic transformations.
   *
   * @param modelName the canonical model namestring for this name
   * @param context the parent element of the element whose name this is
   * @return a string containing a dotted-hierarchy of external names
   */
  @NotNull
  default String externalNameForParent(@NotNull String modelName, @NotNull GradleDslElement context) { return ""; }

  /**
   * Converts a dotted-hierarchy name with hierarchy denoting external names to a dotted-hierarchy name of canonical model names for
   * properties.  Does not perform any syntactic transformations.
   *
   * @param externalName the external dotted-namestring for this name
   * @param context the parent element of the element whose name this is (or will be after parsing)
   * @return a string containing a dotted-hierarchy of model names
   */
  @NotNull
  default String modelNameForParent(@NotNull String externalName, @NotNull GradleDslElement context) { return ""; }
}
