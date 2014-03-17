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
package com.android.tools.idea.gradle.service.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.resolve.GradleMethodContextContributor;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;

import java.util.List;

public class AndroidVariantContributor implements GradleMethodContextContributor {
  @NonNls private static final String BUILD_TYPE_FQCN = "com.android.build.gradle.internal.dsl.BuildTypeDsl";
  @NonNls public static final String PRODUCT_FLAVOR_FQCN = "com.android.build.gradle.internal.dsl.ProductFlavorDsl";

  @Override
  public void process(@NotNull List<String> methodCallInfo,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    if (!(place instanceof GrReferenceExpressionImpl)) {
      return;
    }

    if (methodCallInfo.isEmpty() || methodCallInfo.size() < 3 ||
        !"android".equals(methodCallInfo.get(2))) {
      return;
    }

    final String container = methodCallInfo.get(1);
    String fqName;
    if ("buildTypes".equals(container)) {
      fqName = BUILD_TYPE_FQCN;
    }
    else if ("productFlavors".equals(container)) {
      fqName = PRODUCT_FLAVOR_FQCN;
    }
    else {
      return;
    }

    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    PsiClass contributorClass = psiManager.findClassWithCache(fqName, place.getResolveScope());
    if (contributorClass == null) {
      return;
    }

    GradleResolverUtil.addImplicitVariable(processor, state, (GrReferenceExpressionImpl)place, fqName);
  }
}
