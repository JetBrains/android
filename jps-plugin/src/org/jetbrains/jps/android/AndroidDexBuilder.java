/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.android;

import com.android.sdklib.BuildToolInfo;
import com.android.tools.idea.jps.AndroidTargetBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.android.compiler.tools.AndroidDxRunner;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.builder.AndroidDexBuildTarget;
import org.jetbrains.jps.android.builder.AndroidPreDexBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ExternalProcessUtil;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.StopBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDexBuilder extends AndroidTargetBuilder<BuildRootDescriptor, AndroidDexBuildTarget> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidDexBuilder");
  @NonNls private static final String DEX_BUILDER_NAME = "Android Dex";
  @NonNls private static final String PRO_GUARD_BUILDER_NAME = "ProGuard";

  public AndroidDexBuilder() {
    super(Collections.singletonList(AndroidDexBuildTarget.MyTargetType.INSTANCE));
  }

  @Override
  protected void buildTarget(@NotNull final AndroidDexBuildTarget buildTarget,
                             @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidDexBuildTarget> holder,
                             @NotNull BuildOutputConsumer outputConsumer,
                             @NotNull CompileContext context) throws ProjectBuildException, IOException {
    assert !AndroidJpsUtil.isLightBuild(context);

    try {
      if (!doDexBuild(buildTarget, context, holder.hasDirtyFiles() || holder.hasRemovedFiles(), outputConsumer)) {
        throw new StopBuildException();
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, DEX_BUILDER_NAME, LOG);
    }
  }

  private static boolean isPredexingInScope(@NotNull CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    return context.getScope().isAffected(new AndroidPreDexBuildTarget(project));
  }

  private static boolean doDexBuild(@NotNull AndroidDexBuildTarget target,
                                    @NotNull CompileContext context,
                                    boolean hasDirtyFiles,
                                    @NotNull BuildOutputConsumer outputConsumer) throws IOException {
    final JpsModule module = target.getModule();

    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    assert extension != null;
    assert !extension.isLibrary();

    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, DEX_BUILDER_NAME);
    if (platform == null) {
      return false;
    }

    File dexOutputDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
    dexOutputDir = AndroidJpsUtil.createDirIfNotExist(dexOutputDir, context, DEX_BUILDER_NAME);
    if (dexOutputDir == null) {
      return false;
    }

    final ProGuardOptions proGuardOptions = AndroidJpsUtil.getProGuardConfigIfShouldRun(context, extension);

    if (proGuardOptions != null) {
      if (proGuardOptions.getCfgFiles() == null) {
        context.processMessage(new CompilerMessage(DEX_BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle
                                                     .message("android.jps.errors.cannot.find.proguard.cfg", module.getName())));
        return false;
      }
    }
    final File proguardCfgOutputFile = new File(dexOutputDir, AndroidCommonUtils.PROGUARD_CFG_OUTPUT_FILE_NAME);

    final AndroidProGuardStateStorage proGuardOptionsStorage =
      context.getProjectDescriptor().dataManager.getStorage(target, AndroidProGuardOptionsStorageProvider.INSTANCE);

    final AndroidProGuardStateStorage.MyState oldProGuardState = proGuardOptionsStorage.read();

    final Set<String> fileSet;
    AndroidProGuardStateStorage.MyState newProGuardState = null;

    try {
      if (proGuardOptions != null) {
        final List<String> proguardCfgFilePathsList = new ArrayList<String>();

        for (File file : proGuardOptions.getCfgFiles()) {
          proguardCfgFilePathsList.add(file.getAbsolutePath());
        }
        proguardCfgFilePathsList.add(proguardCfgOutputFile.getPath());
        final String[] proguardCfgFilePaths = ArrayUtil.toStringArray(proguardCfgFilePathsList);
        final String outputJarPath =
          FileUtil.toSystemDependentName(dexOutputDir.getPath() + '/' + AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME);

        final Pair<Boolean, AndroidProGuardStateStorage.MyState> pair = runProguardIfNecessary(
          extension, target, platform, context, outputJarPath, proguardCfgFilePaths,
          hasDirtyFiles, oldProGuardState);

        if (pair == null) {
          // error reported
          return false;
        }

        if (!pair.getFirst()) {
          // nothing changed
          return true;
        }
        newProGuardState = pair.getSecond();
        assert newProGuardState != null;
        fileSet = Collections.singleton(outputJarPath);
      }
      else {
        if (!hasDirtyFiles && oldProGuardState == null) {
          return true;
        }
        final List<BuildRootDescriptor> roots = context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context);
        fileSet = new HashSet<String>();
        final boolean predexingEnabled = extension.isPreDexingEnabled() && isPredexingInScope(context);

        for (BuildRootDescriptor root : roots) {
          final File rootFile = root.getRootFile();

          if (!rootFile.exists()) {
            continue;
          }

          if (root instanceof AndroidDexBuildTarget.MyClassesDirBuildRootDescriptor) {
            final AndroidDexBuildTarget.ClassesDirType type =
              ((AndroidDexBuildTarget.MyClassesDirBuildRootDescriptor)root).getClassesDirType();

            if (type == AndroidDexBuildTarget.ClassesDirType.JAVA) {
              fileSet.add(rootFile.getPath());
            }
            else if (type == AndroidDexBuildTarget.ClassesDirType.ANDROID_APP) {
              AndroidJpsUtil.addSubdirectories(rootFile, fileSet);
            }
          }
          else if (root instanceof AndroidDexBuildTarget.MyJarBuildRootDescriptor) {
            if (((AndroidDexBuildTarget.MyJarBuildRootDescriptor)root).isPreDexed() == predexingEnabled) {
              fileSet.add(rootFile.getPath());
            }
          }
        }
      }
      final boolean success;

      if (fileSet.size() > 0) {
        final String[] files = new String[fileSet.size()];
        int i = 0;
        for (String filePath : fileSet) {
          files[i++] = FileUtil.toSystemDependentName(filePath);
        }
        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.dex", module.getName())));
        Arrays.sort(files);

        success = runDex(platform, dexOutputDir.getPath(), files, context, module, outputConsumer);
      }
      else {
        success = true;
      }

      if (success) {
        proGuardOptionsStorage.update(newProGuardState);
      }
      return success;
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, DEX_BUILDER_NAME);
      return false;
    }
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return DEX_BUILDER_NAME;
  }

  private static boolean runDex(@NotNull AndroidPlatform platform,
                               @NotNull String outputDir,
                               @NotNull String[] compileTargets,
                               @NotNull CompileContext context,
                               @NotNull JpsModule module,
                               @NotNull BuildOutputConsumer outputConsumer) throws IOException {
    final String outFilePath = outputDir + File.separatorChar + AndroidCommonUtils.CLASSES_FILE_NAME;
    return runDex(platform, outFilePath, compileTargets, context, module.getProject(), outputConsumer,
                  DEX_BUILDER_NAME, module.getName(), module);
  }

  public static boolean runDex(@NotNull AndroidPlatform platform,
                               @NotNull String outFilePath,
                               @NotNull String[] compileTargets,
                               @NotNull CompileContext context,
                               @NotNull JpsProject project, @NotNull BuildOutputConsumer outputConsumer,
                               @NotNull String builderName,
                               @NotNull String srcTargetName,
                               @Nullable JpsModule module) throws IOException {
    BuildToolInfo buildToolInfo = platform.getTarget().getBuildToolInfo();
    if (buildToolInfo == null) {
      return false;
    }

    final String dxJarPath = FileUtil.toSystemDependentName(buildToolInfo.getPath(BuildToolInfo.PathId.DX_JAR));
    final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

    final File dxJar = new File(dxJarPath);
    if (testingManager == null && !dxJar.isFile()) {
      context.processMessage(
        new CompilerMessage(builderName, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.cannot.find.file", dxJarPath)));
      return false;
    }

    boolean multiDex = false;
    if (module != null) {
      JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension != null && extension.isMultiDexEnabled()) {
        outFilePath = new File(outFilePath).getParent();
        multiDex = true;
      }
    }

    final List<String> programParamList = new ArrayList<String>();
    programParamList.add(dxJarPath);
    programParamList.add(outFilePath);

    final JpsAndroidDexCompilerConfiguration configuration =
      JpsAndroidExtensionService.getInstance().getDexCompilerConfiguration(project);
    final List<String> vmOptions;

    if (configuration != null) {
      vmOptions = new ArrayList<String>();
      vmOptions.addAll(ParametersListUtil.parse(configuration.getVmOptions()));

      if (!AndroidCommonUtils.hasXmxParam(vmOptions)) {
        vmOptions.add("-Xmx" + configuration.getMaxHeapSize() + "M");
      }
      programParamList.addAll(Arrays.asList("--optimize", Boolean.toString(configuration.isOptimize())));

      if (configuration.isForceJumbo()) {
        programParamList.addAll(Arrays.asList("--forceJumbo", Boolean.TRUE.toString()));
      }

      if (configuration.isCoreLibrary()) {
        programParamList.add("--coreLibrary");
      }
    }
    else {
      vmOptions = Collections.singletonList("-Xmx1024M");
    }
    if (multiDex) {
      JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension != null) {
        programParamList.add("--multi-dex");
        if (!StringUtil.isEmpty(extension.getMainDexList())) {
          programParamList.add("--main-dex-list");
          programParamList.add(extension.getMainDexList());
        }
        if (extension.isMinimalMainDex()) {
          programParamList.add("--minimal-main-dex");
        }
      }
    }
    programParamList.addAll(Arrays.asList(compileTargets));
    programParamList.add("--exclude");

    final List<String> classPath = new ArrayList<String>();
    classPath.add(ClasspathBootstrap.getResourcePath(AndroidDxRunner.class));
    classPath.add(ClasspathBootstrap.getResourcePath(FileUtilRt.class));

    final File outFile = new File(outFilePath);
    if (outFile.exists() && !outFile.isDirectory() && !outFile.delete()) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.WARNING,
                                                 AndroidJpsBundle.message("android.jps.cannot.delete.file", outFilePath)));
    }
    final String javaExecutable = getJavaExecutable(platform, context, builderName);

    if (javaExecutable == null) {
      return false;
    }
    final List<String> commandLine = ExternalProcessUtil
      .buildJavaCommandLine(javaExecutable, AndroidDxRunner.class.getName(),
                            Collections.<String>emptyList(), classPath, vmOptions, programParamList);

    LOG.info(AndroidCommonUtils.command2string(commandLine));

    final String[] commands = ArrayUtil.toStringArray(commandLine);
    final Process process;

    if (testingManager != null) {
      process = testingManager.getCommandExecutor().createProcess(
        commands, Collections.<String, String>emptyMap());
    }
    else {
      process = Runtime.getRuntime().exec(commands);
    }
    final HashMap<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>(3);
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

    AndroidCommonUtils.handleDexCompilationResult(process, StringUtil.join(commandLine, " "), outFilePath, messages, multiDex);

    AndroidJpsUtil.addMessages(context, messages, builderName, srcTargetName);
    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;

    if (success) {
      final List<String> srcFiles = new ArrayList<String>();

      for (String compileTargetPath : compileTargets) {
        final File compileTarget = new File(compileTargetPath);

        if (compileTarget.isFile()) {
          srcFiles.add(compileTargetPath);
        }
        else if(compileTarget.isDirectory()) {
          AndroidJpsUtil.processClassFilesAndJarsRecursively(compileTarget, new Processor<File>() {
            @Override
            public boolean process(File file) {
              if (file.isFile()) {
                srcFiles.add(file.getPath());
              }
              return true;
            }
          });
        }
      }
      outputConsumer.registerOutputFile(outFile, srcFiles);
    }
    return success;
  }

  @Nullable
  private static String getJavaExecutable(@NotNull AndroidPlatform platform, @NotNull CompileContext context, @NotNull String builderName) {
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = platform.getSdk();
    final String jdkName = sdk.getSdkProperties().getData().getJdkName();
    final JpsLibrary javaSdk = context.getProjectDescriptor().getModel().getGlobal().getLibraryCollection().findLibrary(jdkName);
    if (javaSdk == null || !javaSdk.getType().equals(JpsJavaSdkType.INSTANCE)) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                 AndroidJpsBundle.message("android.jps.errors.java.sdk.not.specified", jdkName)));
      return null;
    }
    return JpsJavaSdkType.getJavaExecutable((JpsSdk<?>)javaSdk.getProperties());
  }

  private static Pair<Boolean, AndroidProGuardStateStorage.MyState>
  runProguardIfNecessary(@NotNull JpsAndroidModuleExtension extension,
                         @NotNull AndroidDexBuildTarget target,
                         @NotNull AndroidPlatform platform,
                         @NotNull CompileContext context,
                         @NotNull String outputJarPath,
                         @NotNull String[] proguardCfgPaths,
                         boolean hasDirtyFiles,
                         @Nullable AndroidProGuardStateStorage.MyState oldState)
    throws IOException {
    final JpsModule module = extension.getModule();
    final File[] proguardCfgFiles = new File[proguardCfgPaths.length];

    for (int i = 0; i < proguardCfgFiles.length; i++) {
      proguardCfgFiles[i] = new File(proguardCfgPaths[i]);

      if (!proguardCfgFiles[i].exists()) {
        context.processMessage(new CompilerMessage(PRO_GUARD_BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.find.file", proguardCfgPaths[i])));
        return null;
      }
    }

    final File mainContentRoot = AndroidJpsUtil.getMainContentRoot(extension);
    if (mainContentRoot == null) {
      context.processMessage(new CompilerMessage(PRO_GUARD_BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.main.content.root.not.found", module.getName())));
      return null;
    }

    final String javaExecutable = getJavaExecutable(platform, context, PRO_GUARD_BUILDER_NAME);
    if (javaExecutable == null) {
      return null;
    }

    final File proguardLogsDir = extension.getProguardLogsDir();
    final File logsDir;

    if (proguardLogsDir != null) {
      logsDir = proguardLogsDir;
    }
    else {
      logsDir = new File(mainContentRoot.getPath() + '/' + AndroidCommonUtils.DIRECTORY_FOR_LOGS_NAME);
    }
    final AndroidProGuardStateStorage.MyState newState = new AndroidProGuardStateStorage.MyState(
      proguardCfgFiles);

    if (!hasDirtyFiles && newState.equals(oldState)) {
      return Pair.create(false, null);
    }
    final List<String> classesDirs = new ArrayList<String>();
    final List<String> libClassesDirs = new ArrayList<String>();
    final List<String> externalJars = new ArrayList<String>();
    final List<String> providedJars = new ArrayList<String>();

    final List<BuildRootDescriptor> roots = context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context);

    for (BuildRootDescriptor root : roots) {
      final File rootFile = root.getRootFile();

      if (!rootFile.exists()) {
        continue;
      }

      if (root instanceof AndroidDexBuildTarget.MyClassesDirBuildRootDescriptor) {
        final AndroidDexBuildTarget.ClassesDirType type =
          ((AndroidDexBuildTarget.MyClassesDirBuildRootDescriptor)root).getClassesDirType();

        if (type == AndroidDexBuildTarget.ClassesDirType.JAVA ||
            type == AndroidDexBuildTarget.ClassesDirType.ANDROID_APP) {
          AndroidJpsUtil.addSubdirectories(rootFile, classesDirs);
        }
        else {
          AndroidJpsUtil.addSubdirectories(rootFile, libClassesDirs);
        }
      }
      else if (root instanceof AndroidDexBuildTarget.MyJarBuildRootDescriptor) {
        final AndroidDexBuildTarget.MyJarBuildRootDescriptor jarRoot =
          (AndroidDexBuildTarget.MyJarBuildRootDescriptor)root;
        if (!jarRoot.isLibPackage() && !jarRoot.isPreDexed()) {
          externalJars.add(rootFile.getPath());
        }
      }
      else if (root instanceof AndroidDexBuildTarget.MyProvidedJarBuildRootDescriptor) {
        providedJars.add(rootFile.getPath());
      }
    }
    final String[] classFilesDirOsPaths = ArrayUtil.toStringArray(classesDirs);
    final String[] libClassFilesDirOsPaths = ArrayUtil.toStringArray(libClassesDirs);
    final String[] externalJarOsPaths = ArrayUtil.toStringArray(externalJars);
    final String[] providedJarOsPaths = ArrayUtil.toStringArray(providedJars);
    final String inputJarOsPath = AndroidCommonUtils.buildTempInputJar(classFilesDirOsPaths, libClassFilesDirOsPaths);

    final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

    if (testingManager != null) {
      testingManager.getCommandExecutor().checkJarContent("proguard_input_jar", inputJarOsPath);
    }
    if (!logsDir.exists()) {
      if (!logsDir.mkdirs()) {
        context.processMessage(new CompilerMessage(
          PRO_GUARD_BUILDER_NAME, BuildMessage.Kind.ERROR,
          AndroidJpsBundle.message("android.jps.cannot.create.directory", FileUtil.toSystemDependentName(logsDir.getPath()))));
        return null;
      }
    }
    final JpsAndroidDexCompilerConfiguration configuration =
      JpsAndroidExtensionService.getInstance().getDexCompilerConfiguration(module.getProject());
    String proguardVmOptions = configuration != null ? configuration.getProguardVmOptions() : null;
    if (proguardVmOptions == null) {
      proguardVmOptions = "";
    }
    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.proguard", module.getName())));

    final Map<AndroidCompilerMessageKind, List<String>> messages =
      AndroidCommonUtils.launchProguard(platform.getTarget(), platform.getSdkToolsRevision(), platform.getSdk().getHomePath(),
                                        javaExecutable, proguardVmOptions, proguardCfgPaths, inputJarOsPath, externalJarOsPaths,
                                        providedJarOsPaths, outputJarPath, logsDir.getPath());
    AndroidJpsUtil.addMessages(context, messages, PRO_GUARD_BUILDER_NAME, module.getName());
    return messages.get(AndroidCompilerMessageKind.ERROR).isEmpty()
           ? Pair.create(true, newState) : null;
  }
}
