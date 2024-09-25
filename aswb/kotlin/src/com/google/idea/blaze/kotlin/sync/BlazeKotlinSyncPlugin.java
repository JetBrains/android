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
package com.google.idea.blaze.kotlin.sync;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.idea.blaze.kotlin.sync.KotlinUtils.findToolchain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.KotlinToolchainIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.java.sync.JavaLanguageLevelHelper;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.sdkcompat.kotlin.KotlinCompat;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.config.CompilerSettings;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.facet.KotlinFacetType;

/** Supports Kotlin. */
public class BlazeKotlinSyncPlugin implements BlazeSyncPlugin {
  // we don't get the plugin ID from org.jetbrains.kotlin.idea.KotlinPluginUtil because that
  // requires some integration testing setup (e.g. will throw an exception if idea.home.path isn't
  // set).
  private static final String KOTLIN_PLUGIN_ID = "org.jetbrains.kotlin";
  private static final LanguageVersion DEFAULT_VERSION = LanguageVersion.KOTLIN_1_2;
  private static final BoolExperiment setCompilerFlagsExperiment =
      new BoolExperiment("blaze.kotlin.sync.set.compiler.flags", true);
  // Creates K2JVMCompilerArguments for the .workspace module
  private static final BoolExperiment createK2JVMCompilerArgumentsWorkspaceModuleExperiment =
      new BoolExperiment("blaze.kotlin.sync.create.k2jvmcompilerarguments.workspace.module", true);

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return KotlinUtils.isKotlinSupportEnabled(workspaceType)
        ? ImmutableSet.of(LanguageClass.KOTLIN)
        : ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.KOTLIN)
        ? ImmutableList.of(KOTLIN_PLUGIN_ID)
        : ImmutableList.of();
  }

  /**
   * Ensure the plugin is enabled and that the language version is as intended. The actual
   * installation of the plugin should primarily be handled by {@link AlwaysPresentKotlinSyncPlugin}
   */
  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.KOTLIN)) {
      return;
    }
    updateProjectSettings(project, blazeProjectData);
  }

  /**
   * Update the compiler settings of the project if needed. The language setting applies to both the
   * api version and the language version. Blanket setting this project wide is fine. The rules
   * should catch incorrect usage.
   */
  private static void updateProjectSettings(Project project, BlazeProjectData blazeProjectData) {
    KotlinToolchainIdeInfo kotlinToolchainIdeInfo = findToolchain(blazeProjectData.getTargetMap());
    if (kotlinToolchainIdeInfo == null) {
      return;
    }
    LanguageVersion languageLevel = getLanguageVersion(kotlinToolchainIdeInfo);
    String versionString = languageLevel.getVersionString();
    CommonCompilerArguments settings =
        (CommonCompilerArguments)
            KotlinCompat.unfreezeSettings(
                KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).getSettings());
    boolean updated = false;
    String apiVersion = settings.getApiVersion();
    String languageVersion = settings.getLanguageVersion();
    if (apiVersion == null || !apiVersion.equals(versionString)) {
      updated = true;
      settings.setApiVersion(versionString);
    }
    if (languageVersion == null || !languageVersion.equals(versionString)) {
      updated = true;
      settings.setLanguageVersion(versionString);
    }
    if (updated) {
      KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).setSettings(settings);
    }

    if (setCompilerFlagsExperiment.getValue()) {
      CompilerSettings compilerSettings =
          (CompilerSettings)
              KotlinCompat.unfreezeSettings(
                  KotlinCompilerSettings.Companion.getInstance(project).getSettings());
      // Order matters since we have parameter like -jvm-target 1.8 where two parameters must be
      // aligned in order.
      // Currently, we list all common compiler flags in settings even though it may be duplicated
      // with CommonCompilerArguments.languageVersion and K2JVMCompilerArguments.jvmTarget. There
      // are 2 reasons: 1. they are expected to be identical 2. we do not really use these compiler
      // arguments when compiling kotlin files. They are for the Kotlin plugin in IDE only.
      Set<String> commonFlags =
          new LinkedHashSet<>(kotlinToolchainIdeInfo.getKotlinCompilerCommonFlags());
      Collections.addAll(commonFlags, compilerSettings.getAdditionalArguments().split(" "));
      compilerSettings.setAdditionalArguments(String.join(" ", commonFlags));
      KotlinCompilerSettings.Companion.getInstance(project).setSettings(compilerSettings);
    }
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, final BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.KOTLIN)) {
      return null;
    }
    return new KotlinLibrarySource(blazeProjectData);
  }

  private static LanguageVersion getLanguageVersion(@Nullable KotlinToolchainIdeInfo toolchain) {
    if (toolchain == null) {
      return DEFAULT_VERSION;
    }
    LanguageVersion version = LanguageVersion.fromVersionString(toolchain.getLanguageVersion());
    return version != null ? version : DEFAULT_VERSION;
  }

  @Nullable
  private static Module getWorkspaceModule(Project project) {
    return ReadAction.compute(
        () ->
            ModuleManager.getInstance(project)
                .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME));
  }

  private static KotlinFacet getOrCreateKotlinFacet(Module module) {
    KotlinFacet facet = KotlinFacet.Companion.get(module);
    if (facet != null) {
      return facet;
    }
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet =
          facetManager.createFacet(
              KotlinFacetType.Companion.getINSTANCE(), KotlinFacetType.NAME, null);
      model.addFacet(facet);
    } finally {
      model.commit();
    }
    return facet;
  }

  private static boolean isCompilerOption(String option) {
    return option.startsWith(
        "plugin:" + AndroidCommandLineProcessor.Companion.getANDROID_COMPILER_PLUGIN_ID() + ":");
  }

  @Override
  public void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.KOTLIN)) {
      return;
    }

    KotlinFacet kotlinFacet = getOrCreateKotlinFacet(workspaceModule);
    updatePluginOptions(
        kotlinFacet,
        Arrays.stream(KotlinPluginOptionsProvider.EP_NAME.getExtensions())
            .map(
                provider ->
                    provider.collectKotlinPluginOptions(blazeProjectData.getTargetMap().targets()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));
    setJavaLanguageLevel(
        kotlinFacet,
        JavaLanguageLevelHelper.getJavaLanguageLevel(projectViewSet, blazeProjectData));
  }

  /**
   * This method takes the options that are present on the {@link KotlinPluginOptionsProvider} and
   * adds them as plugin options to the {@link KotlinFacet}. Old options are removed from the facet
   * before configuration of the new options from the extension.
   *
   * @param newPluginOptions new plugin options to be updated to KotlinFacet settings
   */
  private static void updatePluginOptions(KotlinFacet kotlinFacet, List<String> newPluginOptions) {
    var facetSettings = kotlinFacet.getConfiguration().getSettings();
    // TODO: Unify this part with {@link
    // org.jetbrains.kotlin.android.sync.ng.KotlinSyncModels#setupKotlinAndroidExtensionAsFacetPluginOptions}?
    CommonCompilerArguments commonArguments = facetSettings.getCompilerArguments();
    if (commonArguments == null) {
      // Need to initialize to K2JVMCompilerArguments instance to allow Live-Edit to extract the
      // module name. Using K2JVMCompilerArguments.DummyImpl() does not work as it still return
      // CommonCompilerArguments.
      if (createK2JVMCompilerArgumentsWorkspaceModuleExperiment.getValue()) {
        commonArguments = new K2JVMCompilerArguments();
      } else {
        commonArguments = new CommonCompilerArguments.DummyImpl();
      }
    }

    String[] oldPluginOptions = commonArguments.getPluginOptions();
    if (oldPluginOptions == null) {
      oldPluginOptions = new String[0];
    }
    newPluginOptions.addAll(
        Arrays.stream(oldPluginOptions)
            .filter(option -> !isCompilerOption(option))
            .collect(toImmutableList()));
    commonArguments.setPluginOptions(newPluginOptions.toArray(new String[0]));
    facetSettings.setCompilerArguments(commonArguments);
  }

  private static void setJavaLanguageLevel(KotlinFacet kotlinFacet, LanguageLevel languageLevel) {
    Project project = kotlinFacet.getModule().getProject();
    setProjectJvmTarget(project, languageLevel);

    CommonCompilerArguments commonArguments =
        kotlinFacet.getConfiguration().getSettings().getCompilerArguments();
    if (commonArguments instanceof K2JVMCompilerArguments) {
      String javaVersion = languageLevel.toJavaVersion().toString();
      ((K2JVMCompilerArguments) commonArguments).setJvmTarget(javaVersion);
    }
  }

  private static void setProjectJvmTarget(Project project, LanguageLevel javaLanguageLevel) {
    K2JVMCompilerArguments k2JVMCompilerArguments =
        (K2JVMCompilerArguments)
            KotlinCompat.unfreezeSettings(
                Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project).getSettings());

    String javaVersion = javaLanguageLevel.toJavaVersion().toString();
    k2JVMCompilerArguments.setJvmTarget(javaVersion);
    Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project)
        .setSettings(k2JVMCompilerArguments);
  }

  static class Listener implements SyncListener {
    @Override
    public void afterSync(
        Project project,
        BlazeContext context,
        SyncMode syncMode,
        SyncResult syncResult,
        ImmutableSet<Integer> buildIds) {
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (blazeProjectData == null
          || !blazeProjectData
              .getWorkspaceLanguageSettings()
              .isLanguageActive(LanguageClass.KOTLIN)) {
        return;
      }
      Module workspaceModule = getWorkspaceModule(project);
      if (workspaceModule == null) {
        return;
      }
      KotlinCompat.configureModule(project, workspaceModule);
    }
  }
}
