/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.google.common.truth.Expect
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@RunsInEdt
class GradleBuildModelUsageInfoTest : UpgradeGradleFileModelTestCase() {

  @get:Rule val expect: Expect = Expect.createAndEnableStackTrace()
  /**
   * [UsageInfo] instances which compare as .equals() are treated as semantically equivalent by UsageView, and possibly also
   * by the RefactoringProcessor itself.  This means that we must be careful to override the default UsageInfo implementation
   * of equality appropriately for our UsageInfo classes if there is any chance that more than one instance of them might end
   * up in a refactoring with the same underlying [PsiElement] -- which is particularly likely if we are creating new elements
   * (as then the PsiElement is likely to be some common parent element).
   *
   * This test attempts to verify that none of the most-likely [GradleBuildModelUsageInfo] instances collide in that way, but
   * instances here are manually created and curated, so this runs the risk of falling out of sync with the implementation.
   */
  @Test
  fun testNoEquality() {
    val psiElement = mock(PsiElement::class.java)
    `when`(psiElement.isValid).thenReturn(true)
    `when`(psiElement.project).thenReturn(project)

    val virtualFile = mock(VirtualFile::class.java)

    val processor = mock(AgpUpgradeComponentRefactoringProcessor::class.java)
    val wrappedPsiElement = WrappedPsiElement(psiElement, processor, null)


    val gradleBuildModel = mock(GradleBuildModel::class.java)
    val gradlePropertyModel = mock(GradlePropertyModel::class.java)
    val repositoriesModel = mock(RepositoriesModel::class.java)
    val repositoryModel = mock(RepositoryModel::class.java)
    val languageLevelPropertyModel = mock(LanguageLevelPropertyModel::class.java)
    `when`(languageLevelPropertyModel.gradleFile).thenReturn(virtualFile)
    val dependenciesModel = mock(DependenciesModel::class.java)
    val dependencyModel = mock(DependencyModel::class.java)
    val artifactDependencyModel = mock(ArtifactDependencyModel::class.java)
    val configurationModel = mock(ConfigurationModel::class.java)
    val pluginModel = mock(PluginModel::class.java)
    val buildTypeModel = mock(BuildTypeModel::class.java)
    val androidModel = mock(AndroidModel::class.java)
    val resolvedPropertyModel = mock(ResolvedPropertyModel::class.java)

    val usageInfos = listOf(
      AgpVersionUsageInfo(wrappedPsiElement, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.1.0"), gradlePropertyModel),
      RepositoriesNoGMavenUsageInfo(wrappedPsiElement, repositoriesModel, GradleVersion.parse("6.1.1")),
      GradleVersionUsageInfo(wrappedPsiElement, GradleVersion.parse("6.1.1"), "https://services.gradle.org/distributions/gradle-6.1.1-bin.zip"),
      WellKnownGradlePluginDependencyUsageInfo(wrappedPsiElement, artifactDependencyModel, gradlePropertyModel, "1.3.72"),
      JavaLanguageLevelUsageInfo(wrappedPsiElement, languageLevelPropertyModel, false, INSERT_OLD_DEFAULT, "sourceCompatibility"),
      JavaLanguageLevelUsageInfo(wrappedPsiElement, languageLevelPropertyModel, false, INSERT_OLD_DEFAULT, "targetCompatibility"),
      KotlinLanguageLevelUsageInfo(wrappedPsiElement, languageLevelPropertyModel, false, INSERT_OLD_DEFAULT, "jvmTarget"),
      ObsoleteConfigurationDependencyUsageInfo(wrappedPsiElement, dependencyModel, "api"),
      ObsoleteConfigurationDependencyUsageInfo(wrappedPsiElement, dependencyModel, "implementation"),
      ObsoleteConfigurationConfigurationUsageInfo(wrappedPsiElement, configurationModel, "paidReleaseImplementation"),
      RemoveFabricMavenRepositoryUsageInfo(wrappedPsiElement, repositoriesModel, repositoryModel),
      AddGoogleMavenRepositoryUsageInfo(wrappedPsiElement, repositoriesModel, GradleVersion.parse("6.1.1")),
      RemoveFabricClasspathDependencyUsageInfo(wrappedPsiElement, dependenciesModel, dependencyModel),
      AddGoogleServicesClasspathDependencyUsageInfo(wrappedPsiElement, dependenciesModel),
      AddFirebaseCrashlyticsClasspathDependencyUsageInfo(wrappedPsiElement, dependenciesModel),
      ReplaceFabricPluginUsageInfo(wrappedPsiElement, pluginModel),
      ApplyGoogleServicesPluginUsageInfo(wrappedPsiElement, gradleBuildModel),
      RemoveFabricCrashlyticsSdkUsageInfo(wrappedPsiElement, dependenciesModel, dependencyModel),
      AddFirebaseCrashlyticsSdkUsageInfo(wrappedPsiElement, dependenciesModel),
      AddGoogleAnalyticsSdkUsageInfo(wrappedPsiElement, dependenciesModel),
      RemoveFabricNdkUsageInfo(wrappedPsiElement, dependenciesModel, dependencyModel),
      AddFirebaseCrashlyticsNdkUsageInfo(wrappedPsiElement, dependenciesModel),
      RemoveCrashlyticsEnableNdkUsageInfo(wrappedPsiElement, gradleBuildModel),
      AddBuildTypeFirebaseCrashlyticsUsageInfo(wrappedPsiElement, buildTypeModel),
      VIEW_BINDING_ENABLED_INFO.MovePropertyUsageInfo(wrappedPsiElement, resolvedPropertyModel, resolvedPropertyModel),
      DATA_BINDING_ENABLED_INFO.MovePropertyUsageInfo(wrappedPsiElement, resolvedPropertyModel, resolvedPropertyModel),
      SOURCE_SET_JNI_INFO.RemovePropertyUsageInfo(wrappedPsiElement, gradleBuildModel),
      (MIGRATE_AAPT_OPTIONS_TO_ANDROID_RESOURCES.propertiesOperationInfos[0] as MovePropertiesInfo)
        .MovePropertyUsageInfo(wrappedPsiElement, resolvedPropertyModel, resolvedPropertyModel),
    )
    usageInfos.forEach { one ->
      usageInfos.filter { it !== one }.forEach { two ->
        expect.that(one).isNotEqualTo(two)
      }
    }
  }
}