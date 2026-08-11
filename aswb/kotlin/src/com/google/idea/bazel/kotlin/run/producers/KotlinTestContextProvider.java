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
package com.google.idea.bazel.kotlin.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.producers.CommandComponent;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TargetSpecification;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.base.run.producers.TestFilterComponent;
import com.google.idea.blaze.base.run.producers.TestFilterSyntax;
import com.google.idea.blaze.base.run.producers.TestSelector;
import com.google.idea.blaze.base.run.producers.UnifiedRunContext;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

class KotlinTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    return getPsiElement(context).flatMap(KotlinTestContextProvider::getTestContext).orElse(null);
  }

  private static Optional<UnifiedRunContext> getTestContext(PsiElement element) {
    KtNamedFunction testMethod =
        PsiTreeUtil.getParentOfType(element, KtNamedFunction.class, /* strict= */ false);
    KtClass testClass = PsiTreeUtil.getParentOfType(element, KtClass.class, /* strict= */ false);
    if (testClass == null) {
      return Optional.empty();
    }

    FqName fqName = testMethod != null ? testMethod.getFqName() : testClass.getFqName();
    if (fqName == null) {
      return Optional.empty();
    }

    TestSize testSize = getTestSize(testClass, testMethod).orElse(null);
    VirtualFile vf =
        testClass.getContainingFile() != null
            ? testClass.getContainingFile().getVirtualFile()
            : null;
    if (vf == null) {
      return Optional.empty();
    }

    TargetSpecification targetSpec =
        new TargetSpecification.PendingResolution(new File(vf.getPath()), testSize, RuleType.TEST);

    String className =
        testClass.getFqName() != null ? testClass.getFqName().asString() : fqName.asString();
    String methodName = testMethod != null ? testMethod.getName() : null;
    TestSelector testSelector =
        new TestSelector(className, methodName, null, TestFilterSyntax.RULES_KOTLIN);
    PsiElement contextElement = testMethod != null ? testMethod : testClass;

    return Optional.of(
        new UnifiedRunContext(
            contextElement,
            targetSpec,
            new TestFilterComponent(Collections.singletonList(testSelector), false),
            new CommandComponent(BlazeCommandName.TEST, Collections.emptyList())));
  }

  @SuppressWarnings({"rawtypes"})
  private static Optional<PsiElement> getPsiElement(ConfigurationContext context) {
    return Optional.ofNullable(context.getLocation()).map(Location::getPsiElement);
  }

  private static Optional<TestSize> getTestSize(
      KtClass testClass, @Nullable KtNamedFunction testMethod) {
    return testMethod != null
        ? KotlinTestSizeFinder.getTestSize(testMethod)
        : KotlinTestSizeFinder.getTestSize(testClass);
  }
}
