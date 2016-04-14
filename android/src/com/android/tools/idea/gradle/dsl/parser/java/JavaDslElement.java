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
package com.android.tools.idea.gradle.dsl.parser.java;

import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaDslElement extends BaseCompileOptionsDslElement {
  @NonNls public static final String JAVA_BLOCK_NAME = "java";

  public JavaDslElement(@Nullable GradleDslElement parent) {
    super(parent, JAVA_BLOCK_NAME);
  }

  @Override
  @Nullable
  public GroovyPsiElement getPsiElement() {
    return null; // This class just act as an intermediate class for java properties and doesn't represent any real element on the file.
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    return myParent == null ? null : myParent.create();
  }

  @Override
  public void setPsiElement(@Nullable GroovyPsiElement psiElement) {
  }
}
