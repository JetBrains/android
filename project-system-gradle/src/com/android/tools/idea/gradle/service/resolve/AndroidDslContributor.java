/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.resolve.GradleMethodContextContributor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo;

/**
 * {@link AndroidDslContributor} provides symbol resolution for identifiers inside the android block
 * in a Gradle build script.
 */
public class AndroidDslContributor implements GradleMethodContextContributor {
  @Override
  public @Nullable DelegatesToInfo getDelegatesToInfo(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrMethodCallExpression methodCallExpression) {
      if (methodCallExpression.getInvokedExpression().getText().equals("kotlinOptions")) {
        PsiType kotlinOptions = TypesUtil.createType("org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions", closure);
        return new DelegatesToInfo(kotlinOptions, Closure.DELEGATE_FIRST);
      }
    }

    return null;
  }
}
