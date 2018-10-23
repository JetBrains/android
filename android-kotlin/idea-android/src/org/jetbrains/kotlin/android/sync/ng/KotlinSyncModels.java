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
package org.jetbrains.kotlin.android.sync.ng;

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.ExtraGradleSyncAndroidModels;
import com.android.tools.idea.gradle.project.sync.ng.ExtraGradleSyncJavaModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetType;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import kotlin.KotlinVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.config.*;
import org.jetbrains.kotlin.gradle.model.AllOpen;
import org.jetbrains.kotlin.gradle.model.CompilerArguments;
import org.jetbrains.kotlin.gradle.model.KotlinAndroidExtension;
import org.jetbrains.kotlin.gradle.model.KotlinProject;
import org.jetbrains.kotlin.gradle.model.NoArg;
import org.jetbrains.kotlin.gradle.model.SamWithReceiver;
import org.jetbrains.kotlin.gradle.model.SourceSet;
import org.jetbrains.kotlin.idea.configuration.GradleProjectImportHandler;
import org.jetbrains.kotlin.idea.facet.FacetUtilsKt;
import org.jetbrains.kotlin.idea.facet.KotlinFacet;
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration;
import org.jetbrains.kotlin.idea.facet.KotlinFacetType;
import org.jetbrains.kotlin.platform.IdePlatform;
import org.jetbrains.kotlin.platform.IdePlatformKind;
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind;
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind;
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind;
import org.jetbrains.kotlin.utils.PathUtil;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * The models contained within this class are used to configure the Kotlin facet on the IDEA modules from models obtained from Gradle.
 */
public class KotlinSyncModels {
  @NotNull private static final String ANDROID_COMPILER_PLUGIN_ID = AndroidCommandLineProcessor.Companion.getANDROID_COMPILER_PLUGIN_ID();
  @NotNull private static final String ANDROID_PLUGIN_OPTION_PREFIX = "plugin:" + ANDROID_COMPILER_PLUGIN_ID + ":";
  @NotNull private static final String ANDROID_PLUGIN_OPTION_FORMAT_STRING = ANDROID_PLUGIN_OPTION_PREFIX + "%s=%s";

  /**
   * Extra sync module used to configure the Kotlin facets in Android modules for the new sync infrastructure.
   */
  public static class KotlinAndroidSyncModels extends ExtraGradleSyncAndroidModels {
    @NotNull
    @Override
    public Set<Class<?>> getModelTypes() {
      return Sets.newHashSet(KotlinAndroidExtension.class, KotlinProject.class, AllOpen.class, NoArg.class, SamWithReceiver.class);
    }

    @Override
    public void applyModelsToModule(@NotNull GradleModuleModels moduleModels,
                                    @NotNull Module module,
                                    @NotNull IdeModifiableModelsProvider modelsProvider) {
      configureKotlin(moduleModels, module, modelsProvider, true);
    }

    @Override
    public void addModelsToCache(@NotNull Module module, @NotNull CachedModuleModels cache) {
      // Nothing to do. The state of KotlinProject is stored in KotlinFacet.
    }
  }

  /**
   * Extra sync module used to configure the Kotlin facets in plain Kotlin modules (no Android) for the new sync
   * infrastructure. Due to us not being able to reuse some functionality from the
   * {@link GradleProjectImportHandler}s we have had to re-implement the same login here, this needs to be kept
   * up to date.
   */
  public static class KotlinJavaSyncModels extends ExtraGradleSyncJavaModels {

    @NotNull
    @Override
    public Set<Class<?>> getModelTypes() {
      return Sets.newHashSet(KotlinProject.class, AllOpen.class, NoArg.class, SamWithReceiver.class);
    }

    @Override
    public void applyModelsToModule(@NotNull GradleModuleModels moduleModels,
                                    @NotNull Module module,
                                    @NotNull IdeModifiableModelsProvider modelsProvider) {
      configureKotlin(moduleModels, module, modelsProvider, false);
    }

    @Override
    public void addModelsToCache(@NotNull Module module, @NotNull CachedModuleModels cache) {
      // Nothing to do. The state of KotlinProject is stored in KotlinFacet.
    }
  }

  private static void configureKotlin(@NotNull GradleModuleModels moduleModels,
                                      @NotNull Module module,
                                      @NotNull IdeModifiableModelsProvider modelsProvider,
                                      boolean isAndroid) {
    KotlinProject kotlinProject = moduleModels.findModel(KotlinProject.class);
    if (kotlinProject != null) {
      KotlinFacet facet = createAndAddFacet(module, modelsProvider);
      // We must configure the compiler arguments before configuring the Facet since we require the argument to work out the
      // IdePlatform type.
      configureFacet(facet, kotlinProject, modelsProvider);
      configureCompilerArguments(facet, kotlinProject, isAndroid ? getSelectedVariantName(moduleModels) : null, modelsProvider);
      mirrorGradleImportHandlerConfiguration(moduleModels, facet, module);
      // We setup these for non Android modules in order to ensure any old plugin options are removed.
      KotlinAndroidExtension extension = isAndroid ? moduleModels.findModel(KotlinAndroidExtension.class) : null;
      setupKotlinAndroidExtensionAsFacetPluginOptions(facet, extension);
    }
  }


  @NotNull
  private static KotlinFacet createAndAddFacet(@NotNull Module module, @NotNull IdeModifiableModelsProvider ideModelsProvider) {
    ModifiableFacetModel facetModel = ideModelsProvider.getModifiableFacetModel(module);

    // Remove old facet
    KotlinFacet oldFacet = facetModel.getFacetByType(KotlinFacetType.Companion.getTYPE_ID());
    if (oldFacet != null) {
      facetModel.removeFacet(oldFacet);
    }

    // Add new facet
    FacetType<KotlinFacet, KotlinFacetConfiguration> facetType = KotlinFacetType.Companion.getINSTANCE();
    KotlinFacet facet = facetType.createFacet(module, KotlinFacetType.NAME, facetType.createDefaultConfiguration(), null);
    facet.getConfiguration().getSettings().setUseProjectSettings(false);
    facetModel.addFacet(facet);
    return facet;
  }


  @NotNull
  private static String getSelectedVariantName(@NotNull GradleModuleModels models) {
    Variant variant = models.findModel(Variant.class);
    return variant == null ? "debug" : variant.getName();
  }

  private static void configureFacet(@NotNull KotlinFacet facet,
                                     @NotNull KotlinProject kotlinProject,
                                     @NotNull IdeModifiableModelsProvider modelsProvider) {
    // Compiler version is considered the same as the plugin version
    String compilerVersion = kotlinProject.getKotlinVersion();

    // Obtain platform kind either from the plugin or from existing libraries
    IdePlatform platformKind = convertToTargetPlatformKind(kotlinProject.getProjectType(), facet);

    // Obtain coroutines either by the model or gradle properties file
    String coroutines = kotlinProject.getExperimentalFeatures().getCoroutines();
    coroutines =
      coroutines == null ? CoroutineSupport.INSTANCE.getCompilerArgument(LanguageFeature.Coroutines.getDefaultState()) : coroutines;
    LanguageFeature.State coroutinesProperty = CoroutineSupport.INSTANCE.byCompilerArgument(coroutines);

    // Configure facet
    FacetUtilsKt.configureFacet(facet, compilerVersion, coroutinesProperty, platformKind, modelsProvider);
  }

  private static void configureCompilerArguments(@NotNull KotlinFacet facet,
                                                 @NotNull KotlinProject kotlinProject,
                                                 @Nullable String sourceSetName,
                                                 @NotNull IdeModifiableModelsProvider modelsProvider) {
    // If a source set was provided, use it to configure the classpath, otherwise, use "main"
    if (sourceSetName == null) {
      sourceSetName = "main";
    }
    SourceSet sourceSet = getSourceSetByName(kotlinProject, sourceSetName);

    if (sourceSet != null) {
      CompilerArguments compilerArguments = sourceSet.getCompilerArguments();

      List<String> currentCompilerArguments = compilerArguments.getCurrentArguments();
      List<String> defaultCompilerArguments = compilerArguments.getDefaultArguments();
      if (!currentCompilerArguments.isEmpty()) {
        FacetUtilsKt.parseCompilerArgumentsToFacet(currentCompilerArguments, defaultCompilerArguments, facet, modelsProvider);
      }
    }

    FacetUtilsKt.noVersionAutoAdvance(facet);
  }

  @Nullable
  private static IdePlatform convertToTargetPlatformKind(@NotNull KotlinProject.ProjectType projectType, @NotNull KotlinFacet kotlinFacet) {
    if (projectType == KotlinProject.ProjectType.PLATFORM_COMMON) {
      return CommonIdePlatformKind.Platform.INSTANCE;
    }
    else if (projectType == KotlinProject.ProjectType.PLATFORM_JVM) {
      // We select the default platform instead of the one derived from the CompilerArguments since we need to call
      // configureFacet before configureCompilerArguments. Configuring the compiler arguments should correctly overwrite this
      // to the correct platform.
      return JvmIdePlatformKind.INSTANCE.getDefaultPlatform();
    }
    else if (projectType == KotlinProject.ProjectType.PLATFORM_JS) {
      return JsIdePlatformKind.Platform.INSTANCE;
    }
    return null;
  }

  /**
   * Obtains a {@link SourceSet} from a {@link KotlinProject} with a given name.
   *
   * @param kotlinProject the KotlinProject to search for source sets
   * @param sourceSetName the name of the source set to find
   * @return the source set or null if no source set with the given name could be found
   */
  @Nullable
  private static SourceSet getSourceSetByName(@NotNull KotlinProject kotlinProject, @NotNull String sourceSetName) {
    for (SourceSet sourceSet : kotlinProject.getSourceSets()) {
      if (sourceSet.getName().equals(sourceSetName)) {
        return sourceSet;
      }
    }
    return null;
  }

  /**
   * This method takes the options that are present on the {@link KotlinAndroidExtension} and adds them as plugin options to the
   * {@link KotlinFacet}. Old options are removed from the facet before configuration of the new options from the extension.
   *
   * @param facet     the KotlinFacet to be configured
   * @param extension the KotlinAndroidExtension that should be used to obtain the plugin options, if passed null no options will be
   *                  added and all existing options will be removed
   */
  private static void setupKotlinAndroidExtensionAsFacetPluginOptions(@NotNull KotlinFacet facet,
                                                                      @Nullable KotlinAndroidExtension extension) {
    KotlinFacetSettings facetSettings = facet.getConfiguration().getSettings();
    CommonCompilerArguments commonArguments = facetSettings.getCompilerArguments();
    if (commonArguments == null) {
      commonArguments = new CommonCompilerArguments.DummyImpl();
    }

    String[] oldPluginOptions = commonArguments.getPluginOptions();
    if (oldPluginOptions == null) {
      oldPluginOptions = new String[0];
    }

    List<String> newPluginOptions = new ArrayList<>();
    Arrays.stream(oldPluginOptions).filter(option -> !option.startsWith(ANDROID_PLUGIN_OPTION_PREFIX)).forEach(newPluginOptions::add);

    if (extension != null) {
      newPluginOptions.add(String.format(ANDROID_PLUGIN_OPTION_FORMAT_STRING,
                                         AndroidCommandLineProcessor.Companion.getEXPERIMENTAL_OPTION().getName(),
                                         String.valueOf(extension.isExperimental())));
      // If the model exists we must have the kotlin-android-extensions plugin applied.
      newPluginOptions.add(String.format(ANDROID_PLUGIN_OPTION_FORMAT_STRING,
                                         AndroidCommandLineProcessor.Companion.getENABLED_OPTION().getName(), "true"));
      newPluginOptions.add(String.format(ANDROID_PLUGIN_OPTION_FORMAT_STRING,
                                         AndroidCommandLineProcessor.Companion.getDEFAULT_CACHE_IMPL_OPTION().getName(),
                                         extension.getDefaultCacheImplementation()));
    }

    commonArguments.setPluginOptions(newPluginOptions.toArray(new String[0]));
    facetSettings.setCompilerArguments(commonArguments);
  }

  /**
   * This is taken from {@link org.jetbrains.kotlin.idea.configuration.KotlinGradleSourceSetDataServiceKt} and is normally called after
   * {@link #configureCompilerArguments(KotlinFacet, KotlinProject, String, IdeModifiableModelsProvider)}. Though currently the classpath
   * we get back isn't that same as the one obtained via Intellij's models and results in this method emptying the classpath.
   * <p>
   * // TODO: Work out whether this is an issue with our model population and what the correct value for the dependencyClasspath should be.
   *
   * @param kotlinFacet         the facet to be configured
   * @param dependencyClasspath the depenencyClasspath to be used in the configuration
   */
  @SuppressWarnings("unused")
  private static void adjustClasspath(@NotNull KotlinFacet kotlinFacet, @NotNull List<String> dependencyClasspath) {
    if (dependencyClasspath.isEmpty()) {
      return;
    }

    CommonCompilerArguments arguments = kotlinFacet.getConfiguration().getSettings().getCompilerArguments();
    if (!(arguments instanceof K2JVMCompilerArguments)) {
      return;
    }

    String classpath = ((K2JVMCompilerArguments)arguments).getClasspath();
    if (classpath == null || classpath.isEmpty()) {
      return;
    }
    String[] fullClasspath = classpath.split(File.pathSeparator);

    List<String> newClasspath = Lists.newArrayList(fullClasspath);
    newClasspath.removeAll(dependencyClasspath);
    ((K2JVMCompilerArguments)arguments).setClasspath(!newClasspath.isEmpty() ? String.join(File.pathSeparator, newClasspath) : null);
  }

  /**
   * This method implements the same configuration as the {@link GradleProjectImportHandler}.
   * As a result this method should be kept up to date with the set of all registered handlers, for custom handlers however we may
   * not be able to reproduce the required configuration, this is a downside to the new sync infrastructure. In the future we may want to
   *
   * @param models used to obtain the different model types from Gradle
   * @param facet  the facet that should be configured
   * @param module the module object whose facet we are configuring
   */
  private static void mirrorGradleImportHandlerConfiguration(@NotNull GradleModuleModels models,
                                                             @NotNull KotlinFacet facet,
                                                             @NotNull Module module) {
    // Create a fake DataNode to reuse the handlers.
    ModuleData fakeModuleData = new ModuleData("fake", GradleConstants.SYSTEM_ID, "fake", "fake", "fake", "fake");
    DataNode<ModuleData> fakeDataNode = new DataNode<>(ProjectKeys.MODULE, fakeModuleData, null);

    List<GradleProjectImportHandler> handlers = GradleProjectImportHandler.Companion.getInstances(module.getProject());
    for (GradleProjectImportHandler handler : handlers) {
      handler.importByModule(facet, fakeDataNode);
    }

    // For the Kotlin compiler plugins, the model we have (AllOpen, NoArg and SamWithReceiver) are not compatible will the ones that
    // the handlers are looking for. As a result we will have to implement their functionality ourselves. All constants are taken from their
    // respective plugins.

    Map<String, List<String>> supportedPresets = Collections.singletonMap("jpa", Arrays
      .asList("javax.persistence.Entity", "javax.persistence.Embeddable", "javax.persistence.MappedSuperclass"));

    // The AllOpen model
    AllOpen allOpenModel = models.findModel(AllOpen.class);
    if (allOpenModel != null) {
      List<PluginOption> allOpenPluginOptions = new ArrayList<>();
      List<String> allOpenClasspaths = new ArrayList<>();
      configureKotlinPluginOptionsAndClasspaths(allOpenPluginOptions, allOpenClasspaths, allOpenModel.getAnnotations(),
                                                allOpenModel.getPresets(), supportedPresets, Collections.emptyList(),
                                                PathUtil.getKotlinPathsForIdeaPlugin().getAllOpenPluginJarPath());
      applyOptionsFromKotlinCompilerPluginModel(facet, "org.jetbrains.kotlin.allopen", "allopen", allOpenPluginOptions, allOpenClasspaths);
    }

    // The NoArg model
    NoArg noArgModel = models.findModel(NoArg.class);
    if (noArgModel != null) {
      List<PluginOption> noArgPluginOptions = new ArrayList<>();
      List<String> noArgClasspaths = new ArrayList<>();

      PluginOption additionalOption = new PluginOption("invokeInitializers", String.valueOf(noArgModel.isInvokeInitializers()));
      configureKotlinPluginOptionsAndClasspaths(noArgPluginOptions, noArgClasspaths, noArgModel.getAnnotations(),
                                                noArgModel.getPresets(), supportedPresets, Collections.singletonList(additionalOption),
                                                PathUtil.getKotlinPathsForIdeaPlugin().getNoArgPluginJarPath());
      applyOptionsFromKotlinCompilerPluginModel(facet, "org.jetbrains.kotlin.noarg", "noarg", noArgPluginOptions, noArgClasspaths);
    }

    // SamWithReceiver model
    SamWithReceiver samWithReceiverModel = models.findModel(SamWithReceiver.class);
    if (samWithReceiverModel != null) {
      List<PluginOption> samWithReceiverPluginOptions = new ArrayList<>();
      List<String> samWithReceiverClasspaths = new ArrayList<>();
      configureKotlinPluginOptionsAndClasspaths(samWithReceiverPluginOptions, samWithReceiverClasspaths,
                                                samWithReceiverModel.getAnnotations(),
                                                samWithReceiverModel.getPresets(), supportedPresets, Collections.emptyList(),
                                                PathUtil.getKotlinPathsForIdeaPlugin().getSamWithReceiverJarPath());
      applyOptionsFromKotlinCompilerPluginModel(facet, "org.jetbrains.kotlin.samWithReceiver", "sam-with-receiver",
                                                samWithReceiverPluginOptions,
                                                samWithReceiverClasspaths);
    }
  }

  /**
   * Configures the required plugin options and classpath information.
   *
   * @param options           list of options to be populated with information from the models
   * @param classpaths        list of classpaths to be populated with information from the models
   * @param annotations       the annotations that should be used to populate the options
   * @param presets           the list of presents whose options should be included
   * @param supportedPresets  the map of presets and their options
   * @param additionalOptions extra options to be included
   * @param pluginJarPath     the path to the jar of the plugin
   */
  private static void configureKotlinPluginOptionsAndClasspaths(@NotNull List<PluginOption> options,
                                                                @NotNull List<String> classpaths,
                                                                @NotNull List<String> annotations,
                                                                @NotNull List<String> presets,
                                                                @NotNull Map<String, List<String>> supportedPresets,
                                                                @NotNull List<PluginOption> additionalOptions,
                                                                @NotNull File pluginJarPath) {
    List<String> allAnnotations = new ArrayList<>();
    allAnnotations.addAll(annotations);
    allAnnotations.addAll(presets.stream().flatMap((preset) -> supportedPresets.get(preset).stream()).collect(Collectors.toList()));

    options
      .addAll(allAnnotations.stream().map((annotation -> new PluginOption("annotation", annotation))).collect(Collectors.toList()));
    options.addAll(additionalOptions);

    classpaths.add(pluginJarPath.getAbsolutePath());
  }

  private static class PluginOption {
    @NotNull public String key;
    @NotNull public String value;

    private PluginOption(@NotNull String key, @NotNull String value) {
      this.key = key;
      this.value = value;
    }
  }

  /**
   * This implementation is taken from idePluginUtils.kt#modifyCompilerArgumentsForPlugin.
   *
   * @param facet      the facet to configure
   * @param options    the plugin options that need to be passed to the Kotlin compiler
   * @param classpaths the classpaths that should be added
   */
  private static void applyOptionsFromKotlinCompilerPluginModel(@NotNull KotlinFacet facet,
                                                                @NotNull String compilerPluginId,
                                                                @NotNull String pluginName,
                                                                @NotNull List<PluginOption> options,
                                                                @NotNull List<String> classpaths) {
    KotlinFacetSettings settings = facet.getConfiguration().getSettings();

    CommonCompilerArguments commonArguments = settings.getCompilerArguments();
    if (commonArguments == null) {
      commonArguments = new CommonCompilerArguments.DummyImpl();
    }

    String compilerPluginOptionPrefix = String.format("plugin:%s:", compilerPluginId);

    // See CommonCompilerArguments.PLUGIN_OPTION_FORMAT
    List<String> newOptionsForPlugin =
      options.stream().map((option) -> String.format("%s%s=%s", compilerPluginOptionPrefix, option.key, option.value))
        .collect(Collectors.toList());


    // Here we construct the new list of plugin arguments by taking the old arguments, filtering out existing plugin options
    // and adding the new ones.
    String[] oldPluginOptions = commonArguments.getPluginOptions();
    List<String> newPluginOptions = new ArrayList<>();
    if (oldPluginOptions != null) {
      newPluginOptions
        .addAll(
          Arrays.stream(oldPluginOptions).filter((option) -> !option.startsWith(compilerPluginOptionPrefix))
            .collect(Collectors.toList()));
    }
    newPluginOptions.addAll(newOptionsForPlugin);

    // And we need to do the same thing with for the classpath.
    String[] oldPluginClasspaths = commonArguments.getPluginClasspaths();
    List<String> newPluginClasspaths = new ArrayList<>();
    if (oldPluginClasspaths != null) {
      newPluginClasspaths.addAll(Arrays.stream(oldPluginClasspaths).filter((classpath) -> {
        int lastIndexOfFile = Math.max(classpath.lastIndexOf('/'), classpath.lastIndexOf(File.separatorChar));
        if (lastIndexOfFile < 0) {
          return true;
        }
        return !classpath.substring(lastIndexOfFile + 1).matches("(kotlin-)?(maven)?" + pluginName + "-.*//.jar");
      }).collect(Collectors.toList()));
    }
    newPluginClasspaths.addAll(classpaths);

    commonArguments.setPluginOptions(newPluginOptions.toArray(new String[0]));
    commonArguments.setPluginClasspaths(newPluginClasspaths.toArray(new String[0]));

    settings.setCompilerArguments(commonArguments);
  }
}
