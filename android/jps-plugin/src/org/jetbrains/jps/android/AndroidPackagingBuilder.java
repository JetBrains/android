package org.jetbrains.jps.android;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApkBuilder;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.builder.AndroidPackagingBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPackagingBuilder extends TargetBuilder<BuildRootDescriptor, AndroidPackagingBuildTarget> {
  @NonNls private static final String BUILDER_NAME = "Android Packager";


  public AndroidPackagingBuilder() {
    super(Collections.singletonList(AndroidPackagingBuildTarget.MyTargetType.INSTANCE));
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  @Override
  public void build(@NotNull AndroidPackagingBuildTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidPackagingBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException {
    if (AndroidJpsUtil.isLightBuild(context)) {
      return;
    }

    try {
      if (!doPackaging(target, context, target.getModule(), outputConsumer)) {
        throw new ProjectBuildException();
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static boolean doPackaging(@NotNull BuildTarget<?> target,
                                     @NotNull CompileContext context,
                                     @NotNull JpsModule module,
                                     @NotNull BuildOutputConsumer outputConsumer) throws IOException {
    final boolean release = AndroidJpsUtil.isReleaseBuild(context);
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;

    boolean success = true;

    final String apkFileSetStorageName = "apk_builder_file_set" + (release ? "_release" : "_dev");
    final AndroidFileSetStorage.Provider fileSetStorageProvider = new AndroidFileSetStorage.Provider(apkFileSetStorageName);
    final AndroidFileSetStorage apkFileSetStorage = dataManager.getStorage(target, fileSetStorageProvider);

    final String apkBuilderStateStorageName = "apk_builder_config" + (release ? "_release" : "_dev");
    final AndroidApkBuilderConfigStateStorage.Provider builderStateStoragetProvider =
      new AndroidApkBuilderConfigStateStorage.Provider(apkBuilderStateStorageName);
    final AndroidApkBuilderConfigStateStorage apkBuilderConfigStateStorage =
      dataManager.getStorage(target, builderStateStoragetProvider);

    try {
      if (!doPackagingForModule(context, module, apkFileSetStorage, apkBuilderConfigStateStorage,
                                release, outputConsumer)) {
        success = false;
      }
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      success = false;
    }
    return success;
  }

  private static boolean doPackagingForModule(@NotNull CompileContext context,
                                              @NotNull JpsModule module,
                                              @NotNull AndroidFileSetStorage apkFileSetStorage,
                                              @NotNull AndroidApkBuilderConfigStateStorage apkBuilderConfigStateStorage,
                                              boolean release,
                                              @NotNull BuildOutputConsumer outputConsumer) throws IOException {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    if (extension == null || extension.isLibrary()) {
      return true;
    }

    final String[] sourceRoots = AndroidJpsUtil.toPaths(AndroidJpsUtil.getSourceRootsForModuleAndDependencies(module));
    Arrays.sort(sourceRoots);

    final File intArtifactsDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);

    final File moduleOutputDir = ProjectPaths.getModuleOutputDir(module, false);
    if (moduleOutputDir == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.output.dir.not.specified", module.getName())));
      return false;
    }

    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
    if (platform == null) {
      return false;
    }

    final Set<String> externalJarsSet = new HashSet<String>();

    for (String jarPath : AndroidJpsUtil.getExternalLibraries(context, module, platform)) {
      if (new File(jarPath).exists()) {
        externalJarsSet.add(jarPath);
      }
    }
    final File resPackage = getPackagedResourcesFile(module, intArtifactsDir);

    final File classesDexFile = new File(intArtifactsDir.getPath(), AndroidCommonUtils.CLASSES_FILE_NAME);

    final String sdkPath = platform.getSdk().getHomePath();
    final String outputPath = AndroidJpsUtil.getApkPath(extension, moduleOutputDir);
    if (outputPath == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.cannot.compute.output.apk", module.getName())));
      return false;
    }
    final String customKeyStorePath = FileUtil.toSystemDependentName(extension.getCustomDebugKeyStorePath());
    final String[] nativeLibDirs = collectNativeLibsFolders(extension);
    final String resPackagePath = resPackage.getPath();

    final String classesDexFilePath = classesDexFile.getPath();
    final String[] externalJars = ArrayUtil.toStringArray(externalJarsSet);
    Arrays.sort(externalJars);

    final List<AndroidNativeLibData> additionalNativeLibs = extension.getAdditionalNativeLibs();

    final AndroidFileSetState currentFileSetState =
      buildCurrentApkBuilderState(context.getProjectDescriptor().getProject(), resPackagePath, classesDexFilePath, nativeLibDirs, sourceRoots,
                                  externalJars, release);

    final AndroidApkBuilderConfigState currentApkBuilderConfigState =
      new AndroidApkBuilderConfigState(outputPath, customKeyStorePath, additionalNativeLibs);

    if (context.isMake()) {
      final AndroidFileSetState savedApkFileSetState = apkFileSetStorage.getState(module.getName());
      final AndroidApkBuilderConfigState savedApkBuilderConfigState = apkBuilderConfigStateStorage.getState(module.getName());

      if (currentFileSetState.equalsTo(savedApkFileSetState) &&
          currentApkBuilderConfigState.equalsTo(savedApkBuilderConfigState)) {
        return true;
      }
    }
    context
      .processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.packaging", AndroidJpsUtil.getApkName(module))));

    final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApkBuilder
      .execute(resPackagePath, classesDexFilePath, sourceRoots, externalJars, nativeLibDirs, additionalNativeLibs,
               outputPath, release, sdkPath, customKeyStorePath, new MyExcludedSourcesFilter(context.getProjectDescriptor().getProject()));

    if (messages.get(AndroidCompilerMessageKind.ERROR).size() == 0) {
      // todo: collect src files
      outputConsumer.registerOutputFile(new File(outputPath), Collections.<String>emptyList());
    }
    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, module.getName());
    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();

    apkFileSetStorage.update(module.getName(), success ? currentFileSetState : null);
    apkBuilderConfigStateStorage.update(module.getName(), success ? currentApkBuilderConfigState : null);
    return success;
  }

  @SuppressWarnings("unchecked")
  private static AndroidFileSetState buildCurrentApkBuilderState(@NotNull JpsProject project,
                                                                 @NotNull String resPackagePath,
                                                                 @NotNull String classesDexFilePath,
                                                                 @NotNull String[] nativeLibDirs,
                                                                 @NotNull String[] sourceRoots,
                                                                 @NotNull String[] externalJars,
                                                                 boolean release) {
    final List<String> roots = new ArrayList<String>();
    roots.add(resPackagePath);
    roots.add(classesDexFilePath);
    roots.addAll(Arrays.asList(externalJars));

    for (String sourceRootPath : sourceRoots) {
      final List<File> files = new ArrayList<File>();
      AndroidApkBuilder.collectStandardSourceFolderResources(new File(sourceRootPath), files, new MyExcludedSourcesFilter(project));
      roots.addAll(AndroidJpsUtil.toPaths(files));
    }

    for (String nativeLibDir : nativeLibDirs) {
      final List<File> files = new ArrayList<File>();
      AndroidApkBuilder.collectNativeLibraries(new File(nativeLibDir), files, !release);
      roots.addAll(AndroidJpsUtil.toPaths(files));
    }

    return new AndroidFileSetState(roots, Condition.TRUE, false);
  }

  @NotNull
  private static String[] collectNativeLibsFolders(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    final List<String> result = new ArrayList<String>();
    final File libsDir = extension.getNativeLibsDir();

    if (libsDir != null) {
      result.add(libsDir.getPath());
    }

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), true)) {
      final File depLibsDir = depExtension.getNativeLibsDir();
      if (depLibsDir != null) {
        result.add(depLibsDir.getPath());
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  private static File getPackagedResourcesFile(@NotNull JpsModule module, @NotNull File outputDir) {
    return new File(outputDir.getPath(), module.getName() + ".apk.res");
  }

  private static class MyExcludedSourcesFilter implements Condition<File> {
    private final JpsCompilerExcludes myExcludes;

    public MyExcludedSourcesFilter(@NotNull JpsProject project) {
      myExcludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getCompilerExcludes();
    }

    @Override
    public boolean value(File file) {
      return !myExcludes.isExcluded(file);
    }
  }
}
