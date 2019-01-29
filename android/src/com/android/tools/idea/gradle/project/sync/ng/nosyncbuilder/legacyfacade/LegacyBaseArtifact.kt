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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.builder.model.SourceProvider
import com.android.builder.model.level2.DependencyGraphs
import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.BaseArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library.LegacyDependencyGraphs
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldBaseArtifact
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldDependencies
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toSourceProvider
import java.io.File

open class LegacyBaseArtifact(private val baseArtifact: BaseArtifact) : OldBaseArtifact {
  override fun getName(): String = baseArtifact.name
  override fun getCompileTaskName(): String = baseArtifact.compileTaskName
  override fun getAssembleTaskName(): String = baseArtifact.assembleTaskName
  override fun getClassesFolder(): File = baseArtifact.mergedSourceProvider.classesFolder
  override fun getAdditionalClassesFolders(): Set<File> = baseArtifact.mergedSourceProvider.additionalClassesFolders.toSet()
  override fun getJavaResourcesFolder(): File = baseArtifact.mergedSourceProvider.javaResourcesFolder
  override fun getIdeSetupTaskNames(): Set<String> = baseArtifact.ideSetupTaskNames.toSet()
  override fun getGeneratedSourceFolders(): Collection<File> = baseArtifact.mergedSourceProvider.generatedSourceFolders
  override fun getVariantSourceProvider(): SourceProvider? = baseArtifact.mergedSourceProvider.variantSourceSet?.toSourceProvider()
  override fun getMultiFlavorSourceProvider(): SourceProvider? = baseArtifact.mergedSourceProvider.multiFlavorSourceSet?.toSourceProvider()
  override fun getDependencyGraphs(): DependencyGraphs = LegacyDependencyGraphs(baseArtifact.dependencies)

  @Deprecated("Use new dependencies", ReplaceWith("getDependencyGraphs"))
  override fun getDependencies(): OldDependencies = throw UnusedModelMethodException("getDependencies")

  @Deprecated("Use new dependencies", ReplaceWith("getDependencyGraphs"))
  override fun getCompileDependencies(): OldDependencies = throw UnusedModelMethodException("getCompileDependencies")

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "name=$name," +
                                    "compileTaskName=$compileTaskName," +
                                    "assembleTaskName=$assembleTaskName," +
                                    "classesFolder=$classesFolder," +
                                    "additionalClassesFolders=$additionalClassesFolders," +
                                    "javaResourcesFolder=$javaResourcesFolder," +
                                    "ideSetupTaskNames=$ideSetupTaskNames," +
                                    "generatedSourceFolders=$generatedSourceFolders," +
                                    "variantSourceProvider=$variantSourceProvider," +
                                    "multiFlavorSourceProvider=$multiFlavorSourceProvider" +
                                    "dependencyGraphs=$dependencyGraphs" +
                                    "}"
}
