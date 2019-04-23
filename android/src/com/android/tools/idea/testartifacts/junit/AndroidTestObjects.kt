/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.junit

import com.google.common.annotations.VisibleForTesting
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes
import com.intellij.execution.Executor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.TestClassFilter
import com.intellij.execution.junit.TestObject
import com.intellij.execution.junit.TestPackage
import com.intellij.execution.junit.TestsPattern
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.SourceScope
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener

/**
 * Returns a new [GlobalSearchScope] with exclusion rules for unit tests from [TestArtifactSearchScopes] for all modules relevant to the
 * [JUnitConfiguration].
 *
 * @see TestArtifactSearchScopes.getUnitTestExcludeScope
 */
private fun fixScope(originalScope: GlobalSearchScope, jUnitConfiguration: JUnitConfiguration): GlobalSearchScope {
  return (jUnitConfiguration as AndroidJUnitConfiguration).modulesToCompile
    .asSequence()
    .mapNotNull(TestArtifactSearchScopes::get)
    .fold(originalScope) { scope, testArtifactSearchScopes ->
      scope.intersectWith(GlobalSearchScope.notScope(testArtifactSearchScopes.unitTestExcludeScope))
    }
}

/**
 * Android implementation of [TestObject] so the method [getSourceScope] can be overridden. Since [TestObject] is not
 * a final class (many others inherit from it and override its methods), this class receives an instance of another [TestObject] in
 * the constructor and uses it to call the right methods of the subclasses. Uses delegation.
 */
class AndroidTestObject(private val myTestObject: TestObject) : TestObject(myTestObject.configuration, myTestObject.environment) {
  override fun getModulesToCompile(): Array<Module> = myTestObject.modulesToCompile
  override fun checkConfiguration() = myTestObject.checkConfiguration()
  override fun execute(executor: Executor, runner: ProgramRunner<*>) = myTestObject.execute(executor, runner)
  override fun getConfiguration() = myTestObject.configuration

  /**
   * Returns the suggested context menu action name or null.
   * @see com.intellij.execution.configurations.LocatableConfigurationBase.getActionName
   */
  override fun suggestActionName(): String? = myTestObject.suggestActionName()

  override fun getListener(element: PsiElement, configuration: JUnitConfiguration): RefactoringElementListener? {
    return myTestObject.getListener(element, configuration)
  }

  override fun isConfiguredByElement(
    configuration: JUnitConfiguration,
    testClass: PsiClass?,
    testMethod: PsiMethod?,
    testPackage: PsiPackage?,
    testDir: PsiDirectory?
  ): Boolean {
    return myTestObject.isConfiguredByElement(configuration, testClass, testMethod, testPackage, testDir)
  }

  override fun getSourceScope(): SourceScope? {
    return myTestObject.sourceScope?.let { original ->
      object : SourceScope() {
        override fun getProject(): Project {
          return original.project
        }

        override fun getGlobalSearchScope(): GlobalSearchScope {
          return fixScope(original.globalSearchScope, configuration)
        }

        override fun getLibrariesScope(): GlobalSearchScope {
          return fixScope(original.librariesScope, configuration)
        }

        override fun getModulesToCompile(): Array<Module> {
          return original.modulesToCompile
        }
      }
    }
  }
}
/**
 * Android implementation of [TestsPattern] so the method [getClassFilter] can be overridden. This
 * imposes the right [com.intellij.psi.search.GlobalSearchScope] all around [TestsPattern] configurations.
 */
class AndroidTestsPattern(
  configuration: JUnitConfiguration,
  environment: ExecutionEnvironment
) : TestsPattern(configuration, environment) {
  override fun getClassFilter(data: JUnitConfiguration.Data): TestClassFilter {
    val originalFilter = super.getClassFilter(data)
    return originalFilter.intersectionWith(fixScope(originalFilter.scope, configuration))
  }
}

/**
 * Android implementation of [TestPackage] so the method [getClassFilter] can be overridden. This
 * imposes the right [com.intellij.psi.search.GlobalSearchScope] all around [TestPackage] configurations.
 */
class AndroidTestPackage(
  configuration: JUnitConfiguration,
  environment: ExecutionEnvironment
) : TestPackage(configuration, environment) {
  @VisibleForTesting
  public override fun getClassFilter(data: JUnitConfiguration.Data): TestClassFilter {
    val originalFilter = super.getClassFilter(data)
    return originalFilter.intersectionWith(fixScope(originalFilter.scope, configuration))
  }
}
