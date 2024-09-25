/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.test;

import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.java.run.producers.JUnitConfigurationUtil;
import com.google.idea.blaze.java.run.producers.ProducerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.lang.Language;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

/**
 * Producer for run configurations related to 'android_test' and 'android_instrumentation_test'
 * targets in Blaze.
 */
class AndroidTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    if (JUnitConfigurationUtil.isMultipleElementsSelected(context)) {
      return null;
    }
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }

    PsiElement psiElement = location.getPsiElement();
    Language language = psiElement.getLanguage();

    // Java and Kotlin PSIs differ. We cannot use Java PSI methods to detect methods and classes for
    // Kotlin sources, so we handle Kotlin sources explicitly
    if (KotlinLanguage.INSTANCE.equals(language)) {
      KtNamedFunction ktFunction =
          PsiUtils.getParentOfType(psiElement, KtNamedFunction.class, false);
      KtClass ktClass = PsiUtils.getParentOfType(psiElement, KtClass.class, false);
      return ktClass == null ? null : AndroidTestContext.fromClassAndMethod(ktClass, ktFunction);
    }

    PsiMethod testMethod = findTestMethod(location);
    PsiClass testClass =
        testMethod != null ? testMethod.getContainingClass() : JUnitUtil.getTestClass(location);
    return testClass != null ? AndroidTestContext.fromClassAndMethod(testClass, testMethod) : null;
  }

  @Nullable
  private static PsiMethod findTestMethod(Location<?> location) {
    Location<PsiMethod> methodLocation = ProducerUtils.getMethodLocation(location);
    return methodLocation != null ? methodLocation.getPsiElement() : null;
  }
}
