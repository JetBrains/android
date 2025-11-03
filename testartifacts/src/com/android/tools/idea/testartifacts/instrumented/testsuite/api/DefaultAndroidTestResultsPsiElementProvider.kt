/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.api

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.TestArtifactSearchScopes
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement

/**
 * A default implementation of [TestResultsPsiElementProvider] which works for Java class-based
 * tests, e.g. Android instrumentation tests.
 */
class DefaultAndroidTestResultsPsiElementProvider : TestResultsPsiElementProvider {
  private val parameterizedTestMethodNameRegex: Regex = "\\[.*\\]$".toRegex()

  @AnyThread
  override fun isApplicable(runConfiguration: RunConfiguration): Boolean {
    return true
  }

  @UiThread
  override fun getPsiElement(project: Project,
                             androidTestResults: AndroidTestResults,
                             module: Module?): PsiElement? {
    val scopes = module?.let { TestArtifactSearchScopes.getInstance(module) } ?: return null
    val androidTestSourceScope = scopes.androidTestSourceScope
    val javaPsiFacade = JavaPsiFacade.getInstance(project)

    val testClasses = androidTestResults.getFullTestClassName().let {
      javaPsiFacade.findClasses(it, androidTestSourceScope)
    }

    return testClasses.firstNotNullOfOrNull {
      it.findMethodsByName(androidTestResults.methodName, true).firstOrNull()
    }
           ?: testClasses.firstNotNullOfOrNull {
             it.findMethodsByName(androidTestResults.methodName.replace(parameterizedTestMethodNameRegex, ""), true).firstOrNull()
           }
           ?: testClasses.firstOrNull()
  }
}