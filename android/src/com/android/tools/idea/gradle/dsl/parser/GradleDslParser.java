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
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A parser for BUILD.gradle files. Used to build up a {@link GradleBuildModel} from the underlying file.
 *
 * Standard implementations of {@link GradleDslParser} should allow the setting of a {@link GradleDslFile} (e.g as a constructor argument),
 * when {@link #parse()} is called the parser should set the properties obtained onto the {@link GradleDslFile}.
 *
 * The {@link GradleDslParser} also contains several helper methods to work with the language specific subclasses of {@link PsiElement};
 * these are utilized by the {@link GradleBuildModel}.
 *
 * This interface aims to allow the {@link GradleBuildModel} to support different languages, each language should have its
 * own implementation of both {@link GradleDslParser} and {@link GradleDslWriter}.
 *
 * Note: The methods on this interface are marked with whether or not they require read access.
 * Read access can be obtained using {@link Application#runReadAction(Computable)}, among other ways.
 */
public interface GradleDslParser {
  /**
   * Instructs the parser perform its parsing operation. This method REQUIRES read access.
   */
  void parse();

  /**
   * Converts a given {@link Object} to the language specific {@link PsiElement}, this method is used to convert newly set or parsed values.
   * This method does NOT REQUIRE read access.
   */
  @Nullable
  PsiElement convertToPsiElement(@NotNull Object literal);

  /**
   * Extracts a value {@link Object} from a given {@link PsiElement}. The {@code resolve} parameter determines
   * whether or not the returned value should contained resolved references to variables. e.g either "android-${version}" (unresolved)
   * or "android-23" (resolved). A {@link GradleDslExpression} is needed to resolve any variable names that need
   * to be injected.
   *
   * This method REQUIRES read access.
   */
  @Nullable
  Object extractValue(@NotNull GradleDslExpression context, @NotNull PsiElement literal, boolean resolve);

  /**
   * Returns a list of {@link GradleReferenceInjection}s that were derived from {@code psiElement} .
   * A {@link GradleDslExpression} is needed to resolve any variable names that need to be injected.
   *
   * This method REQUIRES read access.
   */
  @NotNull
  List<GradleReferenceInjection> getInjections(@NotNull GradleDslExpression context, @NotNull PsiElement psiElement);

  class Adapter implements GradleDslParser {
    @Override
    public void parse() { }

    @Override
    @Nullable
    public PsiElement convertToPsiElement(@NotNull Object literal) {
      return null;
    }

    @Override
    @Nullable
    public Object extractValue(@NotNull GradleDslExpression context, @NotNull PsiElement literal, boolean resolve) {
      return null;
    }

    @Override
    @NotNull
    public List<GradleReferenceInjection> getInjections(@NotNull GradleDslExpression context, @NotNull PsiElement psiElement) {
      return Collections.emptyList();
    }
  }
}
