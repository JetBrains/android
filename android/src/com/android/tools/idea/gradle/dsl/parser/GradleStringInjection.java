// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.parser;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

/**
 * Represents an injection of one value into another. This class links the {@link GradleDslExpression} that needs to be
 * injected and the {@link PsiElement} of the injection.
 */
public class GradleStringInjection {
  @NotNull
  private GradleDslExpression myToBeInjected;
  @NotNull
  private PsiElement myPsiInjection;
  @NotNull
  private String myName; // The name of the injection, e.g "prop1 = "Hello ${world}" -> "world" or "prop1 = hello" -> "hello"

  public GradleStringInjection(@NotNull GradleDslExpression injection, @NotNull PsiElement psiInjection, @NotNull String name) {
    myToBeInjected = injection;
    myPsiInjection = psiInjection;
    myName = name;
  }

  @NotNull
  public GradleDslExpression getToBeInjected() {
    return myToBeInjected;
  }

  @NotNull
  public PsiElement getPsiInjection() {
    return myPsiInjection;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * Injects all given {@code injections} into a given {@link PsiElement}. These {@link GradleStringInjection}s should have been
   * obtained using {@link GradleDslParser#getInjections(PsiElement)}.
   */
  @NotNull
  public static String injectAll(@NotNull PsiElement psiElement, @NotNull Collection<GradleStringInjection> injections) {
    StringBuilder builder = new StringBuilder();
    for (PsiElement element : psiElement.getChildren()) {
      // Reference equality intended
      Optional<GradleStringInjection> filteredInjection = injections.stream().filter(injection -> element == injection.getPsiInjection()).findFirst();
      if (filteredInjection.isPresent()) {
        Object value = filteredInjection.get().getToBeInjected().getValue();
        builder.append(value == null ? "" : value);
      } else {
        builder.append(element.getText());
      }
    }
    return builder.toString();
  }
}
