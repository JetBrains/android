/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.exportSignedPackage.runsGradle

import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType
import org.jetbrains.android.exportSignedPackage.GradleSigningInfo
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.ExecutionException

@RunWith(Parameterized::class)
class ExportSignedPackageWizardSigningTest(private val targetType: TargetType,
                                           validStorePassword: Boolean,
                                           validKeyAlias: Boolean,
                                           validKeyPassword: Boolean,
                                           private val buildResultHandler: Consumer<ListenableFuture<AssembleInvocationResult>>) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0} ValidStorePass {1}, ValidKeyAlias {2}, ValidKeyPass {3}")
    fun data() = listOf(
      arrayOf(ExportSignedPackageWizard.BUNDLE, true, true, true, successHandler),
      arrayOf(ExportSignedPackageWizard.APK, true, true, true, successHandler),
      arrayOf(ExportSignedPackageWizard.BUNDLE, false, true, true, badStorePassHandler),
      arrayOf(ExportSignedPackageWizard.APK, false, true, true, badStorePassHandler),
      arrayOf(ExportSignedPackageWizard.BUNDLE, true, false, true, badKeyAliasHandler),
      arrayOf(ExportSignedPackageWizard.APK, true, false, true, badKeyAliasHandler),
      arrayOf(ExportSignedPackageWizard.BUNDLE, true, true, false, badKeyPassHandler),
      arrayOf(ExportSignedPackageWizard.APK, true, true, false, badKeyPassHandler),
    )

    private val successHandler = Consumer { future: ListenableFuture<AssembleInvocationResult> ->
      try {
        val result = future.get()
        if (!result.isBuildSuccessful) {
          TestCase.fail(getInvocationErrorsCause(result))
        }
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e)
      }
    }

    private val badStorePassHandler = Consumer { future: ListenableFuture<AssembleInvocationResult> ->
      try {
        val result = future.get()
        if (result.isBuildSuccessful) {
          TestCase.fail("Build was successful even with a bad store password")
        }
        val buildErrorCause: String = getInvocationErrorsCause(result)
        Truth.assertThat(buildErrorCause).contains("Keystore was tampered with, or password was incorrect")
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e)
      }
    }

    private val badKeyAliasHandler = Consumer { future: ListenableFuture<AssembleInvocationResult> ->
      try {
        val result = future.get()
        if (result.isBuildSuccessful) {
          TestCase.fail("Build was successful even with a bad key alias")
        }
        val buildErrorCause: String = getInvocationErrorsCause(result)
        Truth.assertThat(buildErrorCause).contains("No key with alias 'badKeyAlias' found in keystore")
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e)
      }
    }

    private val badKeyPassHandler = Consumer { future: ListenableFuture<AssembleInvocationResult> ->
      try {
        val result = future.get()
        if (result.isBuildSuccessful) {
          TestCase.fail("Build was successful even with a bad key alias")
        }
        val buildErrorCause: String = getInvocationErrorsCause(result)
        Truth.assertThat(buildErrorCause).contains("Failed to read key androiddebugkey from store")
      }
      catch (e: InterruptedException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        throw RuntimeException(e)
      }
    }

    private fun getInvocationErrorsCause(result: AssembleInvocationResult): String {
      val errorMessages = StringBuilder("The build was not successful\n")
      for (invocation in result.invocationResult.invocations) {
        var buildError = invocation.buildError
        var prefix = ""
        while (buildError != null) {
          prefix += "  "
          errorMessages.append(prefix).append(buildError.message).append("\n")
          val cause = buildError.cause
          if (cause === buildError) {
            break
          }
          buildError = cause
        }
      }
      return errorMessages.toString()
    }
  }

  private val storePassword = (if (validStorePassword) "android" else "badStorePass").toCharArray()
  private val keyAlias = if (validKeyAlias) "androiddebugkey" else "badKeyAlias"
  private val keyPassword = (if (validKeyPassword) "android" else "badKeyPass").toCharArray()

  @get:Rule
  var myRule = AndroidGradleProjectRule()

  /**
   * Perform a sign and use the result handler to confirm expected behaviour.
   */
  @Test
  fun testSign() {
    myRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val project: Project = myRule.project
    val facets = project.getAndroidFacets().stream().filter { facet: AndroidFacet -> facet.configuration.isAppProject }.toList()
    Truth.assertThat(facets).isNotEmpty()
    val facet = facets[0]
    val androidModel = GradleAndroidModel.get(facet)
    Truth.assertThat(androidModel).isNotNull()
    val variants = listOf("release")
    val keyStorePath = AndroidTestBase.getTestDataPath() + File.separator + "signingKey" + File.separator + "debug.keystore"
    val signingInfo = GradleSigningInfo(keyStorePath, storePassword, keyAlias, keyPassword)
    val apkPath = androidModel!!.rootDirPath.path
    val modules = listOf(facet.mainModule)
    ExportSignedPackageWizard.doBuildAndSignGradleProject(project, facet, variants, modules, signingInfo, apkPath, targetType,
                                                          buildResultHandler)
  }
}
