package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.android.repository.api.ProgressIndicatorAdapter;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.builder.AndroidAarDepsBuildTarget;
import org.jetbrains.jps.android.model.*;
import org.jetbrains.jps.android.model.impl.JpsAndroidFinalPackageElement;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidJpsUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidJpsUtil");

  @NonNls public static final String ANDROID_FACET_TYPE_ID = "android";
  @NonNls public static final String ANDROID_FACET_NAME = "Android";

  @NonNls public static final String ANDROID_STORAGE_DIR = "android";
  @NonNls private static final String RESOURCE_CACHE_STORAGE = "res_cache";
  @NonNls private static final String INTERMEDIATE_ARTIFACTS_STORAGE = "intermediate_artifacts";

  @NonNls public static final String GENERATED_RESOURCES_DIR_NAME = "generated_resources";
  @NonNls public static final String AAPT_GENERATED_SOURCE_ROOT_NAME = "aapt";
  @NonNls public static final String AIDL_GENERATED_SOURCE_ROOT_NAME = "aidl";
  @NonNls public static final String RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME = "rs";
  @NonNls public static final String BUILD_CONFIG_GENERATED_SOURCE_ROOT_NAME = "build_config";
  @NonNls private static final String GENERATED_SOURCES_FOLDER_NAME = "generated_sources";
  @NonNls private static final String PREPROCESSED_MANIFEST_FOLDER_NAME = "preprocessed_manifest";
  @NonNls private static final String COPIED_SOURCES_FOLDER_NAME = "copied_sources";
  @NonNls private static final String MANIFEST_TAG = "manifest";

  private AndroidJpsUtil() {
  }

  /**
   * In a module imported from Maven dependencies are transitive, so we don't need to traverse all dependency tree
   * and compute all jars referred by library modules. Moreover it would be incorrect,
   * because Maven has dependency resolving algorithm based on versioning
   */
  public static boolean shouldProcessDependenciesRecursively(JpsModule module) {
    return JpsJavaDependenciesEnumerationHandler.shouldProcessDependenciesRecursively(
      JpsJavaDependenciesEnumerationHandler.createHandlers(Collections.singletonList(module)));
  }

  @Nullable
  public static File getMainContentRoot(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    final JpsModule module = extension.getModule();

    final List<String> contentRoots = module.getContentRootsList().getUrls();

    if (contentRoots.size() == 0) {
      return null;
    }
    final File manifestFile = extension.getManifestFile();

    if (manifestFile != null) {
      for (String rootUrl : contentRoots) {
        final File root = JpsPathUtil.urlToFile(rootUrl);

        if (FileUtil.isAncestor(root, manifestFile, true)) {
          return root;
        }
      }
    }
    return JpsPathUtil.urlToFile(contentRoots.get(0));
  }

  public static void addMessages(@NotNull CompileContext context,
                                 @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                 @NotNull String builderName,
                                 @NotNull String entryName) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      for (String message : entry.getValue()) {
        String filePath = null;
        int line = -1;
        final Matcher matcher = AndroidCommonUtils.COMPILER_MESSAGE_PATTERN.matcher(message);

        if (matcher.matches()) {
          filePath = matcher.group(1);
          line = Integer.parseInt(matcher.group(2));
        }
        final BuildMessage.Kind category = toBuildMessageKind(entry.getKey());
        if (category != null) {
          context.processMessage(
            new CompilerMessage(builderName, category, '[' + entryName + "] " + message, filePath, -1L, -1L, -1L, line, -1L));
        }
      }
    }
  }

  @Nullable
  public static JpsAndroidModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsAndroidModuleExtensionImpl.KIND);
  }

  @NotNull
  public static String[] toPaths(@NotNull File[] files) {
    final String[] result = new String[files.length];

    for (int i = 0; i < result.length; i++) {
      result[i] = files[i].getPath();
    }
    return result;
  }

  @NotNull
  public static List<String> toPaths(@NotNull Collection<File> files) {
    if (files.size() == 0) {
      return Collections.emptyList();
    }

    final List<String> result = new ArrayList<String>(files.size());
    for (File file : files) {
      result.add(file.getPath());
    }
    return result;
  }

  @NotNull
  public static File getDirectoryForIntermediateArtifacts(@NotNull CompileContext context,
                                                          @NotNull JpsModule module) {
    return getDirectoryForIntermediateArtifacts(context.getProjectDescriptor().dataManager.getDataPaths(), module);
  }

  @NotNull
  public static File getDirectoryForIntermediateArtifacts(@NotNull BuildDataPaths dataPaths,
                                                          @NotNull JpsModule module) {
    return new File(getDirectoryForIntermediateArtifacts(dataPaths), module.getName());
  }

  @NotNull
  public static File getDirectoryForIntermediateArtifacts(@NotNull BuildDataPaths dataPaths) {
    final File androidStorage = new File(dataPaths.getDataStorageRoot(), ANDROID_STORAGE_DIR);
    return new File(androidStorage, INTERMEDIATE_ARTIFACTS_STORAGE);
  }

  @Nullable
  public static File createDirIfNotExist(@NotNull File dir, @NotNull CompileContext context, @NotNull String compilerName) {
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", dir.getPath())));
        return null;
      }
    }
    return dir;
  }

  public static void addSubdirectories(@NotNull File baseDir, @NotNull Collection<String> result) {
    // only include files inside packages
    final File[] children = baseDir.listFiles();

    if (children != null) {
      for (File child : children) {
        if (child.isDirectory()) {
          result.add(child.getPath());
        }
      }
    }
  }

  @NotNull
  public static Set<String> getExternalLibraries(@NotNull CompileContext context,
                                                 @NotNull JpsModule module,
                                                 @NotNull AndroidPlatform platform) {
    final BuildDataPaths paths = context.getProjectDescriptor().dataManager.getDataPaths();
    return getExternalLibraries(paths, module, platform, true, true, false);
  }

  @NotNull
  public static Set<String> getExternalLibraries(@NotNull BuildDataPaths paths,
                                                 @NotNull JpsModule module,
                                                 @Nullable AndroidPlatform platform,
                                                 boolean resolveJars,
                                                 boolean withAarDeps,
                                                 boolean withPackagedAarDepsJar) {
    final Set<String> result = new HashSet<String>();
    final AndroidDependencyProcessor processor = new AndroidDependencyProcessor() {
      @Override
      public void processExternalLibrary(@NotNull File file) {
        result.add(file.getPath());
      }

      @Override
      public boolean isToProcess(@NotNull AndroidDependencyType type) {
        return type == AndroidDependencyType.EXTERNAL_LIBRARY;
      }
    };
    processClasspath(paths, module, processor, resolveJars, withAarDeps);

    if (platform != null) {
      addAnnotationsJarIfNecessary(platform, result);
    }
    if (withPackagedAarDepsJar) {
      result.add(AndroidAarDepsBuildTarget.getOutputFile(module, paths).getPath());
    }
    return result;
  }

  @NotNull
  public static Set<String> getProvidedLibraries(@NotNull BuildDataPaths paths,
                                                 @NotNull JpsModule module) {
    final Set<String> result = new HashSet<String>();
    processClasspath(paths, module, new AndroidDependencyProcessor() {
      @Override
      public void processProvidedLibrary(@NotNull File file) {
        result.add(file.getPath());
      }

      @Override
      public boolean isToProcess(@NotNull AndroidDependencyType type) {
        return type == AndroidDependencyType.PROVIDED_LIBRARY;
      }
    }, false, false);
    return result;
  }

  private static void addAnnotationsJarIfNecessary(@NotNull AndroidPlatform platform, @NotNull Set<String> libs) {
    if (platform.needToAddAnnotationsJarToClasspath()) {
      final String sdkHomePath = platform.getSdk().getHomePath();
      final String annotationsJarPath = FileUtil.toSystemIndependentName(sdkHomePath) + AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;
      libs.add(annotationsJarPath);
    }
  }

  public static void processClasspath(@NotNull BuildDataPaths paths,
                                      @NotNull JpsModule module,
                                      @NotNull AndroidDependencyProcessor processor,
                                      boolean resolveJars,
                                      boolean withAarDeps) {
    final boolean recursive = shouldProcessDependenciesRecursively(module);
    processClasspath(paths, module, processor, new HashSet<String>(), false, recursive, resolveJars, withAarDeps);
  }

  private static void processClasspath(@NotNull BuildDataPaths paths,
                                       @NotNull final JpsModule module,
                                       @NotNull final AndroidDependencyProcessor processor,
                                       @NotNull final Set<String> visitedModules,
                                       final boolean exportedLibrariesOnly,
                                       final boolean recursive,
                                       final boolean resolveJars,
                                       final boolean withAarDeps) {
    if (!visitedModules.add(module.getName())) {
      return;
    }
    if (processor.isToProcess(AndroidDependencyType.EXTERNAL_LIBRARY)) {
      for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module,
                                                                                             JpsJavaClasspathKind.PRODUCTION_RUNTIME,
                                                                                             exportedLibrariesOnly)) {
        if (item instanceof JpsLibraryDependency) {
          final JpsLibrary library = ((JpsLibraryDependency)item).getLibrary();

          if (library != null && (withAarDeps || getResDirAndJarsIfAar(library) == null)) {
            for (JpsLibraryRoot root : library.getRoots(JpsOrderRootType.COMPILED)) {
              final File file = JpsPathUtil.urlToFile(root.getUrl());

              if (resolveJars) {
                processClassFilesAndJarsRecursively(file, new Processor<File>() {
                  @Override
                  public boolean process(File file) {
                    processor.processExternalLibrary(file);
                    return true;
                  }
                });
              }
              else {
                processor.processExternalLibrary(file);
              }
            }
          }
        }
      }
    }
    if (processor.isToProcess(AndroidDependencyType.PROVIDED_LIBRARY)) {
      for (JpsDependencyElement item : module.getDependenciesList().getDependencies()) {
        if (item instanceof JpsLibraryDependency) {
          final JpsLibrary library = ((JpsLibraryDependency)item).getLibrary();
          final JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(item);

          if (library != null && extension != null && extension.getScope() == JpsJavaDependencyScope.PROVIDED) {
            for (JpsLibraryRoot root : library.getRoots(JpsOrderRootType.COMPILED)) {
              processor.processProvidedLibrary(JpsPathUtil.urlToFile(root.getUrl()));
            }
          }
        }
      }
    }

    for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false)) {
      if (item instanceof JpsModuleDependency) {
        final JpsModule depModule = ((JpsModuleDependency)item).getModule();
        if (depModule == null) continue;
        final JpsAndroidModuleExtension depExtension = getExtension(depModule);
        final boolean depLibrary = depExtension != null && depExtension.isLibrary();
        final File depClassDir = new ModuleBuildTarget(depModule, JavaModuleBuildTargetType.PRODUCTION).getOutputDir();

        if (depLibrary) {
          if (processor.isToProcess(AndroidDependencyType.ANDROID_LIBRARY_PACKAGE)) {
            final File intArtifactsDir = getDirectoryForIntermediateArtifacts(paths, depModule);
            final File packagedClassesJar = new File(intArtifactsDir, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
            processor.processAndroidLibraryPackage(packagedClassesJar, depModule);
          }
          if (processor.isToProcess(AndroidDependencyType.ANDROID_LIBRARY_OUTPUT_DIRECTORY)) {
            if (depClassDir != null) {
              processor.processAndroidLibraryOutputDirectory(depClassDir);
            }
          }
        }
        else if (processor.isToProcess(AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR) &&
                 depExtension == null &&
                 depClassDir != null) {
          // do not support android-app->android-app compile dependencies
          processor.processJavaModuleOutputDirectory(depClassDir);
        }
        if (recursive) {
          final boolean newRecursive = shouldProcessDependenciesRecursively(depModule);
          processClasspath(paths, depModule, processor, visitedModules,
                           !depLibrary || exportedLibrariesOnly, newRecursive, resolveJars, withAarDeps);
        }
      }
    }
  }

  public static void processClassFilesAndJarsRecursively(@NotNull File root, @NotNull final Processor<File> processor) {
    FileUtil.processFilesRecursively(root, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile()) {
          String fileName = file.getName();

          // NOTE: we should ignore apklib dependencies (IDEA-82976)
          if (FileUtilRt.extensionEquals(fileName, "jar") || FileUtilRt.extensionEquals(fileName, "class")) {
            if (!processor.process(file)) {
              return false;
            }
          }
        }
        return true;
      }
    });
  }

  @Nullable
  public static Pair<IAndroidTarget, AndroidSdkHandler> getAndroidTarget(@NotNull JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk,
                                                                  @Nullable CompileContext context,
                                                                  String builderName) {
    JpsAndroidSdkProperties sdkProperties = sdk.getSdkProperties().getData();
    final String targetHashString = sdkProperties.getBuildTargetHashString();
    if (targetHashString == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   "Cannot parse SDK " + sdk.getParent().getName() + ": build target is not specified"));
      }
      return null;
    }

    final AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(new File(sdk.getHomePath()));

    RepoLogger log = new RepoLogger();
    final IAndroidTarget target = sdkHandler.getAndroidTargetManager(log).getTargetFromHashString(targetHashString, log);
    if (target == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   "Cannot parse SDK '" + sdk.getParent().getName() +
                                                   "': unknown target " + targetHashString));
      }
      return null;
    }
    return Pair.create(target, sdkHandler);
  }

  @Nullable
  public static BuildMessage.Kind toBuildMessageKind(@NotNull AndroidCompilerMessageKind kind) {
    switch (kind) {
      case ERROR:
        return BuildMessage.Kind.ERROR;
      case INFORMATION:
        return BuildMessage.Kind.INFO;
      case WARNING:
        return BuildMessage.Kind.WARNING;
      default:
        LOG.error("unknown AndroidCompilerMessageKind object " + kind);
        return null;
    }
  }

  public static void reportExceptionError(@NotNull CompileContext context,
                                          @Nullable String filePath,
                                          @NotNull Exception exception,
                                          @NotNull String builderName) {
    final String message = exception.getMessage();

    if (message != null) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR, message, filePath));
      LOG.debug(exception);
    }
    else {
      context.processMessage(new CompilerMessage(builderName, exception));
    }
  }

  public static ModuleLevelBuilder.ExitCode handleException(@NotNull CompileContext context,
                                                            @NotNull Exception e,
                                                            @NotNull String builderName,
                                                            @Nullable Logger logger) throws ProjectBuildException {
    if (logger != null) {
      logger.info(e);
    }
    context.processMessage(new CompilerMessage(builderName, e));
    throw new StopBuildException();
  }

  @Nullable
  public static File getManifestFileForCompilationPath(@NotNull JpsAndroidModuleExtension extension) {
    return extension.useCustomManifestForCompilation()
           ? extension.getManifestFileForCompilation()
           : extension.getManifestFile();
  }

  @Nullable
  public static AndroidPlatform getAndroidPlatform(@NotNull JpsModule module,
                                                   @Nullable CompileContext context,
                                                   String builderName) {
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = module.getSdk(JpsAndroidSdkType.INSTANCE);
    if (sdk == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.sdk.not.specified", module.getName())));
      }
      return null;
    }

    final Pair<IAndroidTarget, AndroidSdkHandler> pair = getAndroidTarget(sdk, context, builderName);
    if (pair == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.sdk.invalid", module.getName())));
      }
      return null;
    }
    return new AndroidPlatform(sdk, pair.getFirst(), pair.getSecond());
  }

  @NotNull
  public static String[] collectResourceDirsForCompilation(@NotNull JpsAndroidModuleExtension extension,
                                                           boolean withCacheDirs,
                                                           @NotNull CompileContext context,
                                                           boolean checkExistence) {
    final BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    return collectResourceDirsForCompilation(extension, withCacheDirs, dataPaths, checkExistence);
  }

  @NotNull
  public static String[] collectResourceDirsForCompilation(@NotNull JpsAndroidModuleExtension extension,
                                                           boolean withCacheDirs,
                                                           @NotNull BuildDataPaths dataPaths,
                                                           boolean checkExistence) {
    final Collection<String> result = new OrderedSet<String>();
    addCompilableResourceDirsForModule(extension, withCacheDirs, dataPaths, result, checkExistence);

    for (JpsAndroidModuleExtension depExtension : getAllAndroidDependencies(extension.getModule(), true)) {
      addCompilableResourceDirsForModule(depExtension, withCacheDirs, dataPaths, result, checkExistence);
    }
    return ArrayUtil.toStringArray(result);
  }

  private static void addCompilableResourceDirsForModule(JpsAndroidModuleExtension extension,
                                                         boolean withCacheDirs,
                                                         BuildDataPaths dataPaths,
                                                         final Collection<String> result,
                                                         boolean checkExistence) {
    if (withCacheDirs) {
      final File resourcesCacheDir = getResourcesCacheDir(extension.getModule(), dataPaths);
      if (!checkExistence || resourcesCacheDir.exists()) {
        result.add(resourcesCacheDir.getPath());
      }
    }
    for (File resOverlayDir : extension.getResourceOverlayDirs()) {
      if (resOverlayDir != null && (!checkExistence || resOverlayDir.exists())) {
        result.add(resOverlayDir.getPath());
      }
    }
    final File resDir = getResourceDirForCompilationPath(extension);

    if (resDir != null && (!checkExistence || resDir.exists())) {
      result.add(resDir.getPath());
    }
    final File generatedResourcesStorage = getGeneratedResourcesStorage(extension.getModule(), dataPaths);
    if (!checkExistence || generatedResourcesStorage.exists()) {
      result.add(generatedResourcesStorage.getPath());
    }
    collectResDirectoriesFromAarDeps(extension.getModule(), result);
  }

  @Nullable
  public static File getResourceDirForCompilationPath(@NotNull JpsAndroidModuleExtension extension) {
    return extension.useCustomResFolderForCompilation()
           ? extension.getResourceDirForCompilation()
           : extension.getResourceDir();
  }

  @NotNull
  public static List<JpsAndroidModuleExtension> getAllAndroidDependencies(@NotNull JpsModule module, boolean librariesOnly) {
    return AndroidBuildDataCache.getInstance().getAllAndroidDependencies(module, librariesOnly);
  }

  public static boolean isLightBuild(@NotNull CompileContext context) {
    final String typeId = getRunConfigurationTypeId(context);
    return typeId != null && AndroidCommonUtils.isTestConfiguration(typeId);
  }

  @Nullable
  public static String getRunConfigurationTypeId(@NotNull CompileContext context) {
    return context.getBuilderParameter("RUN_CONFIGURATION_TYPE_ID");
  }

  public static boolean isReleaseBuild(@NotNull CompileContext context) {
    if (Boolean.parseBoolean(context.getBuilderParameter(AndroidCommonUtils.RELEASE_BUILD_OPTION))) {
      return true;
    }

    for (JpsArtifact artifact : getAndroidArtifactsToBuild(context)) {
      final JpsElement props = artifact.getProperties();

      if (props instanceof JpsAndroidApplicationArtifactProperties) {
        final AndroidArtifactSigningMode signingMode = ((JpsAndroidApplicationArtifactProperties)props).getSigningMode();

        if (signingMode != AndroidArtifactSigningMode.DEBUG && signingMode != AndroidArtifactSigningMode.DEBUG_WITH_CUSTOM_CERTIFICATE) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static List<JpsArtifact> getAndroidArtifactsToBuild(@NotNull CompileContext context) {
    final List<JpsArtifact> artifacts = JpsArtifactService.getInstance().getArtifacts(context.getProjectDescriptor().getProject());
    final List<JpsArtifact> result = new ArrayList<JpsArtifact>();

    for (JpsArtifact artifact : artifacts) {
      if (artifact.getArtifactType() instanceof AndroidApplicationArtifactType &&
          context.getScope().isAffected(new ArtifactBuildTarget(artifact))) {
        result.add(artifact);
      }
    }
    return result;
  }

  @NotNull
  public static File getResourcesCacheDir(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File androidStorage = new File(dataPaths.getDataStorageRoot(), ANDROID_STORAGE_DIR);
    return new File(new File(androidStorage, RESOURCE_CACHE_STORAGE), module.getName());
  }

  @NotNull
  public static File[] getSourceRootsForModuleAndDependencies(@NotNull JpsModule rootModule) {
    final Set<File> result = new HashSet<File>();

    for (JpsModule module : getRuntimeModuleDeps(rootModule)) {
      final JpsAndroidModuleExtension extension = getExtension(module);
      File resDir = null;
      File resDirForCompilation = null;

      if (extension != null) {
        resDir = extension.getResourceDir();
        resDirForCompilation = extension.getResourceDirForCompilation();
      }

      for (JpsModuleSourceRoot root : module.getSourceRoots()) {
        final File rootDir = JpsPathUtil.urlToFile(root.getUrl());

        if ((JavaSourceRootType.SOURCE.equals(root.getRootType())
             || JavaSourceRootType.TEST_SOURCE.equals(root.getRootType()) && extension != null && extension.isPackTestCode())
            && !FileUtil.filesEqual(rootDir, resDir) && !rootDir.equals(resDirForCompilation)) {
          result.add(rootDir);
        }
      }
    }
    return result.toArray(new File[result.size()]);
  }

  @NotNull
  public static File[] getJavaOutputRootsForModuleAndDependencies(@NotNull JpsModule rootModule) {
    final Set<File> result = new HashSet<File>();

    for (JpsModule module : getRuntimeModuleDeps(rootModule)) {
      final JpsAndroidModuleExtension extension = getExtension(module);
      final File outputDir = JpsJavaExtensionService.getInstance().getOutputDirectory(module, false);

      if (outputDir != null) {
        result.add(outputDir);
      }
      if (extension != null && extension.isPackTestCode()) {
        final File testOutputDir = JpsJavaExtensionService.getInstance().getOutputDirectory(module, true);

        if (testOutputDir != null) {
          result.add(testOutputDir);
        }
      }
    }
    return result.toArray(new File[result.size()]);
  }

  private static Set<JpsModule> getRuntimeModuleDeps(JpsModule rootModule) {
    return JpsJavaExtensionService.getInstance().enumerateDependencies(
      Collections.singletonList(rootModule)).recursively().runtimeOnly().getModules();
  }

  @Nullable
  public static String getApkPath(@NotNull JpsAndroidModuleExtension extension, @NotNull File outputDirForPackagedArtifacts) {
    final String apkRelativePath = extension.getApkRelativePath();
    final JpsModule module = extension.getModule();

    if (apkRelativePath == null || apkRelativePath.length() == 0) {
      return new File(outputDirForPackagedArtifacts, getApkName(module)).getPath();
    }

    File moduleBaseDirectory = JpsModelSerializationDataService.getBaseDirectory(module);
    return moduleBaseDirectory != null ? FileUtil.toSystemDependentName(moduleBaseDirectory.getAbsolutePath() + apkRelativePath) : null;
  }

  @NotNull
  public static String getApkName(@NotNull JpsModule module) {
    return module.getName() + ".apk";
  }

  @NotNull
  public static File getGeneratedSourcesStorage(@NotNull JpsModule module, BuildDataManager dataManager) {
    return getGeneratedSourcesStorage(module, dataManager.getDataPaths());
  }

  @NotNull
  public static File getGeneratedSourcesStorage(@NotNull JpsModule module, final BuildDataPaths dataPaths) {
    final File targetDataRoot = dataPaths.getTargetDataRoot(
      new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    return getStorageDir(targetDataRoot, GENERATED_SOURCES_FOLDER_NAME);
  }

  @NotNull
  public static File getPreprocessedManifestDirectory(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File androidStorage = new File(dataPaths.getDataStorageRoot(), ANDROID_STORAGE_DIR);
    return new File(new File(androidStorage, PREPROCESSED_MANIFEST_FOLDER_NAME), module.getName());
  }

  @Nullable
  public static File getPreprocessedManifestFile(@NotNull JpsAndroidModuleExtension extension, @NotNull BuildDataPaths dataPaths) {
    if (extension.isLibrary() || !extension.isManifestMergingEnabled()) {
      return getManifestFileForCompilationPath(extension);
    }
    final File dir = getPreprocessedManifestDirectory(extension.getModule(), dataPaths);
    return new File(dir, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @NotNull
  public static File getCopiedSourcesStorage(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File targetDataRoot = dataPaths.getTargetDataRoot(
      new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    return getStorageDir(targetDataRoot, COPIED_SOURCES_FOLDER_NAME);
  }

  @NotNull
  public static File getGeneratedResourcesStorage(@NotNull JpsModule module, BuildDataManager dataManager) {
    return getGeneratedResourcesStorage(module, dataManager.getDataPaths());
  }

  @NotNull
  private static File getGeneratedResourcesStorage(@NotNull JpsModule module, @NotNull BuildDataPaths dataPaths) {
    final File targetDataRoot = dataPaths.getTargetDataRoot(
      new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    return getStorageDir(targetDataRoot, GENERATED_RESOURCES_DIR_NAME);
  }

  @NotNull
  public static File getStorageFile(@NotNull File dataStorageRoot, @NotNull String storageName) {
    return new File(getStorageDir(dataStorageRoot, storageName), storageName);
  }

  @NotNull
  public static File getStorageDir(@NotNull File dataStorageRoot, @NotNull String storageName) {
    return new File(new File(dataStorageRoot, ANDROID_STORAGE_DIR), storageName);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  private static Properties readPropertyFile(@NotNull File file) {
    final Properties properties = new Properties();
    try {
      properties.load(new FileInputStream(file));
      return properties;
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  @Nullable
  public static Pair<String, File> getProjectPropertyValue(@NotNull JpsAndroidModuleExtension extension, @NotNull String propertyKey) {
    final File root;
    try {
      root = getMainContentRoot(extension);
    }
    catch (IOException e) {
      return null;
    }
    if (root == null) {
      return null;
    }
    final File projectProperties = new File(root, SdkConstants.FN_PROJECT_PROPERTIES);

    if (projectProperties.exists()) {
      final Properties properties = readPropertyFile(projectProperties);

      if (properties != null) {
        final String value = properties.getProperty(propertyKey);

        if (value != null) {
          return Pair.create(value, projectProperties);
        }
      }
    }
    return null;
  }


  @NotNull
  public static Set<String> getGenDirs(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    final Set<String> result = new HashSet<String>();
    File dir = extension.getAaptGenDir();

    if (dir != null) {
      result.add(dir.getPath());
    }
    dir = extension.getAidlGenDir();

    if (dir != null) {
      result.add(dir.getPath());
    }
    return result;
  }

  @Nullable
  public static JpsAndroidModuleExtension getPackagedFacet(@NotNull JpsArtifact artifact) {
    final List<JpsAndroidModuleExtension> facets = getAllPackagedFacets(artifact);
    return facets.size() == 1 ? facets.get(0) : null;
  }

  @NotNull
  public static List<JpsAndroidModuleExtension> getAllPackagedFacets(JpsArtifact artifact) {
    final List<JpsAndroidModuleExtension> extensions = new ArrayList<JpsAndroidModuleExtension>();

    JpsArtifactUtil.processPackagingElements(artifact.getRootElement(), new Processor<JpsPackagingElement>() {
      @Override
      public boolean process(JpsPackagingElement element) {
        if (element instanceof JpsAndroidFinalPackageElement) {
          final JpsModuleReference reference = ((JpsAndroidFinalPackageElement)element).getModuleReference();
          final JpsModule module = reference != null ? reference.resolve() : null;
          final JpsAndroidModuleExtension extension = module != null ? getExtension(module) : null;

          if (extension != null) {
            extensions.add(extension);
          }
        }
        return true;
      }
    });
    return extensions;
  }

  @NotNull
  private static File[] toFiles(@NotNull String[] paths) {
    final File[] files = new File[paths.length];

    for (int i = 0; i < paths.length; i++) {
      files[i] = new File(paths[i]);
    }
    return files;
  }

  public static ProGuardOptions getProGuardConfigIfShouldRun(@NotNull CompileContext context, @NotNull JpsAndroidModuleExtension extension)
    throws IOException {
    if (extension.isRunProguard()) {
      return new ProGuardOptions(extension.getProguardConfigFiles(extension.getModule()));
    }

    final String cfgPathsStrFromContext = context.getBuilderParameter(AndroidCommonUtils.PROGUARD_CFG_PATHS_OPTION);
    if (cfgPathsStrFromContext != null && cfgPathsStrFromContext.length() > 0) {
      final String[] paths = cfgPathsStrFromContext.split(File.pathSeparator);

      if (paths.length > 0) {
        final File[] files = toFiles(paths);
        return new ProGuardOptions(Arrays.asList(files));
      }
    }

    for (JpsArtifact artifact : getAndroidArtifactsToBuild(context)) {
      final JpsAndroidModuleExtension facetFromArtifact = getPackagedFacet(artifact);
      final JpsModule moduleFromArtifact = facetFromArtifact != null ? facetFromArtifact.getModule() : null;

      if (moduleFromArtifact != null && moduleFromArtifact.equals(extension.getModule())) {
        final JpsElement props = artifact.getProperties();

        if (props instanceof JpsAndroidApplicationArtifactProperties) {
          final JpsAndroidApplicationArtifactProperties androidProps = (JpsAndroidApplicationArtifactProperties)props;

          if (androidProps.isRunProGuard()) {
            final List<String> cfgFileUrls = androidProps.getProGuardCfgFiles(moduleFromArtifact);
            final List<File> cfgPaths = cfgFileUrls != null ? urlsToFiles(cfgFileUrls) : null;
            return new ProGuardOptions(cfgPaths);
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public static List<File> urlsToFiles(@NotNull List<String> urls) {
    if (urls.isEmpty()) {
      return Collections.emptyList();
    }
    final List<File> result = new ArrayList<File>();

    for (String path : urls) {
      result.add(JpsPathUtil.urlToFile(path));
    }
    return result;
  }

  /**
   * Indicates whether the given project is a non-Gradle Android project.
   *
   * @param project the given project.
   * @return {@code true} if the the given project is a non-Gradle Android project, {@code false} otherwise.
   */
  public static boolean isAndroidProjectWithoutGradleFacet(@NotNull JpsProject project) {
    return isAndroidProjectWithoutGradleFacet(project.getModules());
  }

  /**
   * Indicates whether the given modules belong to a non-Gradle Android project.
   *
   * @param chunk the given modules.
   * @return {@code true} if the the given modules belong to a non-Gradle Android project, {@code false} otherwise.
   */
  public static boolean isAndroidProjectWithoutGradleFacet(@NotNull ModuleChunk chunk) {
    return isAndroidProjectWithoutGradleFacet(chunk.getModules());
  }

  private static boolean isAndroidProjectWithoutGradleFacet(@NotNull Collection<JpsModule> modules) {
    boolean hasAndroidFacet = false;
    for (JpsModule module : modules) {
      JpsAndroidModuleExtension androidFacet = getExtension(module);
      if (androidFacet != null) {
        hasAndroidFacet = true;
        if (androidFacet.isGradleProject()) {
          return false;
        }
      }
    }
    return hasAndroidFacet;
  }

  public static void collectRTextFilesFromAarDeps(@NotNull JpsModule module, @NotNull Collection<Pair<String, String>> result) {
    final ArrayList<String> resDirs = new ArrayList<String>();
    collectResDirectoriesFromAarDeps(module, resDirs);

    for (String dir : resDirs) {
      final File parent = new File(dir).getParentFile();
      final File manifestFile = new File(parent, SdkConstants.FN_ANDROID_MANIFEST_XML);
      final File rTxt = new File(parent, SdkConstants.FN_RESOURCE_TEXT);

      if (manifestFile.exists() && rTxt.exists()) {
        try {
          final String packageName = parsePackageNameFromManifestFile(manifestFile);

          if (packageName != null && packageName.length() > 0) {
            result.add(Pair.create(rTxt.getPath(), packageName));
          }
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }
  }

  public static void collectResDirectoriesFromAarDeps(@NotNull JpsModule module, @NotNull Collection<String> result) {
    final Set<JpsLibrary> libs =
      JpsJavaExtensionService.getInstance().enumerateDependencies(Collections.singletonList(module)).
        runtimeOnly().productionOnly().getLibraries();

    for (JpsLibrary lib : libs) {
      final Pair<File, List<File>> pair = getResDirAndJarsIfAar(lib);
      final File resDir = pair != null ? pair.getFirst() : null;

      if (resDir != null) {
        result.add(resDir.getPath());
      }
    }
  }

  @Nullable
  public static Pair<File, List<File>> getResDirAndJarsIfAar(@NotNull JpsLibrary lib) {
    final List<File> files = lib.getFiles(JpsOrderRootType.COMPILED);

    if (files.size() == 1) {
      final File file = files.get(0);

      if (file.isDirectory() && SdkConstants.FD_RES.equals(file.getName())) {
        return Pair.create(file, null);
      }
    }
    else if (files.size() >= 2) {
      File resDir = null;
      File classesJar = null;
      List<File> allJars = new ArrayList<File>();

      for (File file : files) {
        if (file.isDirectory()) {
          if (SdkConstants.FD_RES.equals(file.getName())) {
            resDir = file;
          }
          else {
            return null;
          }
        }
        else if (file.isFile()) {
          if (SdkConstants.FN_CLASSES_JAR.equals(file.getName())) {
            classesJar = file;
          }
          if (FileUtilRt.extensionEquals(file.getName(), "jar")) {
            allJars.add(file);
          }
          else {
            return null;
          }
        }
      }
      if (resDir != null && classesJar != null && FileUtil.pathsEqual(resDir.getParent(), classesJar.getParent())) {
        return Pair.create(resDir, allJars);
      }
    }
    return null;
  }

  @Nullable
  public static String parsePackageNameFromManifestFile(@NotNull File manifestFile) throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(manifestFile));
    try {
      final Ref<String> packageName = new Ref<String>(null);
      FormsParsing.parse(inputStream, new FormsParsing.IXMLBuilderAdapter() {
        boolean processingManifestTagAttrs = false;

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          if (MANIFEST_TAG.equals(name)) {
            processingManifestTagAttrs = true;
          }
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
          throws Exception {
          if (value != null && AndroidCommonUtils.PACKAGE_MANIFEST_ATTRIBUTE.equals(key)) {
            packageName.set(value.trim());
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          stop();
        }
      });

      return packageName.get();
    }
    finally {
      inputStream.close();
    }
  }

  public static class RepoLogger extends ProgressIndicatorAdapter {
    private final Logger myLogger;

    public RepoLogger() {
      myLogger = Logger.getInstance(AndroidJpsUtil.class);
    }

    @Override
    public void logWarning(@NotNull String s) {
      myLogger.warn(s);
    }

    @Override
    public void logWarning(@NotNull String s, @Nullable Throwable e) {
      myLogger.warn(s, e);
    }

    @Override
    public void logError(@NotNull String s) {
      myLogger.error(s);
    }

    @Override
    public void logError(@NotNull String s, @Nullable Throwable e) {
      myLogger.error(s, e);
    }

    @Override
    public void logInfo(@NotNull String s) {
      myLogger.info(s);
    }
  }
}
