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
package com.google.idea.blaze.kotlin.qsync;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.FreezableKt;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.facet.KotlinFacetType;
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins;

/** Supports Kotlin. */
public class BlazeKotlinQuerySyncPlugin implements BlazeQuerySyncPlugin {

  private static final BoolExperiment qsyncDisableCompose =
      new BoolExperiment("qsync.disable.compose", false);

  @Override
  public void updateProjectSettingsForQuerySync(
      Project project, Context<?> context, ProjectViewSet projectViewSet) {
    if (!isKotlinProject(projectViewSet)) {
      return;
    }

    // Set jvm-target from java language level
    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_21);
    setProjectJvmTarget(project, javaLanguageLevel);
  }

  @Override
  public void updateProjectStructureForQuerySync(
      Project project,
      Context<?> context,
      IdeModifiableModelsProvider models,
      WorkspaceRoot workspaceRoot,
      Module workspaceModule,
      Set<String> androidResourceDirectories,
      Set<String> androidSourcePackages,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    KotlinFacet kotlinFacet = getOrCreateKotlinFacet(models, workspaceModule);
    // TODO(xinruiy): makes BlazeGoogle3AndroidKotlinPluginOptionsProvider#hasParcelizeDependency
    // compatible with query sync. So that we can
    // collect new plugin options for query sync targets.
    updatePluginOptions(kotlinFacet, new ArrayList<>());
  }

  private static KotlinFacet getOrCreateKotlinFacet(
      IdeModifiableModelsProvider models, Module module) {
    KotlinFacet facet = KotlinFacet.Companion.get(module);
    if (facet != null) {
      return facet;
    }
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel modifiableFacetModel = models.getModifiableFacetModel(module);
    facet =
        facetManager.createFacet(
            KotlinFacetType.Companion.getINSTANCE(), KotlinFacetType.NAME, null);
    modifiableFacetModel.addFacet(facet);
    return facet;
  }

  private static void updatePluginOptions(KotlinFacet kotlinFacet, List<String> newPluginOptions) {
    var facetSettings = kotlinFacet.getConfiguration().getSettings();
    CommonCompilerArguments commonArguments = facetSettings.getCompilerArguments();
    if (commonArguments == null) {
      commonArguments = new K2JVMCompilerArguments();
    }

    if (KotlinPluginModeProvider.Companion.isK2Mode() && !qsyncDisableCompose.getValue()) {
      // Register the bundled directly, as KtCompilerPluginsProviderIdeImpl consistently replaces
      // user's plugin class path with it.
      // Note: This implementation may need updating if the Kotlin plugin alters its provider
      // replacement logic.
      commonArguments.setPluginClasspaths(
          new String[] {
            KotlinK2BundledCompilerPlugins.COMPOSE_COMPILER_PLUGIN
                .getBundledJarLocation()
                .toString(),
          });
    }
    commonArguments.setPluginOptions(newPluginOptions.toArray(new String[0]));
    facetSettings.setCompilerArguments(commonArguments);
  }

  private static void setProjectJvmTarget(Project project, LanguageLevel javaLanguageLevel) {
    K2JVMCompilerArguments k2JVMCompilerArguments =
        (K2JVMCompilerArguments)
            FreezableKt.unfrozen(
                Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project).getSettings());

    String javaVersion = javaLanguageLevel.toJavaVersion().toString();
    k2JVMCompilerArguments.setJvmTarget(javaVersion);
    Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project)
        .setSettings(k2JVMCompilerArguments);
  }

  private static boolean isKotlinProject(ProjectViewSet projectViewSet) {
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    return workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN);
  }
}
