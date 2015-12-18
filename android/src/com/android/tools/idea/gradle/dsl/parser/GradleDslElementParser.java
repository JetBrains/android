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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * Parses a specific element of a build.gradle file.
 *
 * @deprecated Use {@link com.android.tools.idea.gradle.dsl.GradleDslParser} directly.
 */
public interface GradleDslElementParser {
  /**
   * Attempts to parse the given PSI element.
   *
   * @param e the PSI element to parse.
   * @param gradlePsiFile represents the build.gradle file being parsed.
   * @return {@code true} if this parser was able to parse the given PSI element; {@code false} otherwise.
   */
  boolean parse(@NotNull GroovyPsiElement e, @NotNull GradleDslFile gradlePsiFile);
}
