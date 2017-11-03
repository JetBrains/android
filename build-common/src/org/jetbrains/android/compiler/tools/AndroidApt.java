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
package org.jetbrains.android.compiler.tools;

import com.android.SdkConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.build.SymbolLoader;
import com.android.sdklib.internal.build.SymbolWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidExecutionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * AndroidApt decorator.
 *
 * @author Alexey Efimov
 */
public final class AndroidApt {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidApt");

  @NonNls private static final String COMMAND_CRUNCH = "crunch";
  @NonNls private static final String COMMAND_PACKAGE = "package";

  private static final FileFilter PNG_FILES_FILTER = new FileFilter() {
    @Override
    public boolean accept(File file) {
      return file.isDirectory() || FileUtilRt.extensionEquals(file.getName(), AndroidCommonUtils.PNG_EXTENSION);
    }
  };

  private AndroidApt() {
  }

  public static Map<AndroidCompilerMessageKind, List<String>> compile(@NotNull IAndroidTarget target,
                                                                      int platformToolsRevision,
                                                                      @NotNull String manifestFileOsPath,
                                                                      @NotNull String aPackage,
                                                                      @NotNull String outDirOsPath,
                                                                      @NotNull String[] resourceDirsOsPaths,
                                                                      @NotNull String[] extraPackages,
                                                                      boolean nonConstantFields,
                                                                      @Nullable String proguardCfgOutputFileOsPath) throws IOException {
    final List<Pair<String, String>> libRTxtFilesAndPackages = new ArrayList<Pair<String, String>>();

    for (String extraPackage : extraPackages) {
      libRTxtFilesAndPackages.add(Pair.create((String)null, extraPackage));
    }
    return compile(target, platformToolsRevision, manifestFileOsPath, aPackage, outDirOsPath, resourceDirsOsPaths,
                   libRTxtFilesAndPackages, nonConstantFields, proguardCfgOutputFileOsPath, null, false);
  }

  public static Map<AndroidCompilerMessageKind, List<String>> compile(@NotNull IAndroidTarget target,
                                                                      int platformToolsRevision,
                                                                      @NotNull String manifestFileOsPath,
                                                                      @NotNull String aPackage,
                                                                      @NotNull String outDirOsPath,
                                                                      @NotNull String[] resourceDirsOsPaths,
                                                                      @NotNull List<Pair<String, String>> libRTxtFilesAndPackages,
                                                                      boolean nonConstantFields,
                                                                      @Nullable String proguardCfgOutputFileOsPath,
                                                                      @Nullable String rTxtOutDirOsPath,
                                                                      boolean optimizeRFile) throws IOException {
    final Map<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>();
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

    final File outOsDir = new File(outDirOsPath);
    if (!outOsDir.exists()) {
      if (!outOsDir.mkdirs()) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to create directory " + outDirOsPath);
      }
    }

    final String packageFolderOsPath = FileUtil.toSystemDependentName(outDirOsPath + '/' + aPackage.replace('.', '/'));

    /* We actually need to delete the manifest.java as it may become empty and
    in this case aapt doesn't generate an empty one, but instead doesn't
    touch it */
    final File manifestJavaFile = new File(packageFolderOsPath + File.separatorChar + AndroidCommonUtils.MANIFEST_JAVA_FILE_NAME);
    if (manifestJavaFile.exists()) {
      if (!FileUtil.delete(manifestJavaFile)) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to delete " + manifestJavaFile.getPath());
      }
    }

    final File rJavaFile = new File(packageFolderOsPath + File.separatorChar + AndroidCommonUtils.R_JAVA_FILENAME);
    if (rJavaFile.exists()) {
      if (!FileUtil.delete(rJavaFile)) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to delete " + rJavaFile.getPath());
      }
    }

    final File[] extraRJavaFiles = new File[libRTxtFilesAndPackages.size()];

    for (int i = 0, n = libRTxtFilesAndPackages.size(); i < n; i++) {
      final String libPackage = libRTxtFilesAndPackages.get(i).getSecond();
      final String libPackageFolderOsPath = FileUtil.toSystemDependentName(outDirOsPath + '/' + libPackage.replace('.', '/'));
      extraRJavaFiles[i] = new File(libPackageFolderOsPath + File.separatorChar + AndroidCommonUtils.R_JAVA_FILENAME);
    }

    for (File extraRJavaFile : extraRJavaFiles) {
      if (extraRJavaFile.exists()) {
        if (!FileUtil.delete(extraRJavaFile)) {
          messages.get(AndroidCompilerMessageKind.ERROR).add("Unable to delete " + extraRJavaFile.getPath());
        }
      }
    }

    if (platformToolsRevision < 0 || platformToolsRevision > 7) {
      Map<AndroidCompilerMessageKind, List<String>> map =
        doCompile(target, manifestFileOsPath, outDirOsPath, resourceDirsOsPaths, libRTxtFilesAndPackages,
                  null, nonConstantFields, proguardCfgOutputFileOsPath, rTxtOutDirOsPath, optimizeRFile);

      if (map.get(AndroidCompilerMessageKind.ERROR).isEmpty()) {
        makeFieldsNotFinal(extraRJavaFiles);
      }

      AndroidExecutionUtil.addMessages(messages, map);
      return messages;
    }
    else {
      messages.get(AndroidCompilerMessageKind.ERROR).add(
        "'Platform Tools' package is out of date. Please update it through Android SDK manager");
      return messages;
    }
  }

  private static void makeFieldsNotFinal(@NotNull File[] libRJavaFiles) throws IOException {
    for (File file : libRJavaFiles) {
      if (file.isFile()) {
        final String fileContent = AndroidCommonUtils.readFile(file);
        FileUtil.writeToFile(file, fileContent.replace("public static final int ", "public static int "));
      }
    }
  }

  private static Map<AndroidCompilerMessageKind, List<String>> doCompile(@NotNull IAndroidTarget target,
                                                                         @NotNull String manifestFileOsPath,
                                                                         @NotNull String outDirOsPath,
                                                                         @NotNull String[] resourceDirsOsPaths,
                                                                         @NotNull List<Pair<String, String>> libRTxtFilesAndPackages,
                                                                         @Nullable String customPackage,
                                                                         boolean nonConstantIds,
                                                                         @Nullable String proguardCfgOutputFileOsPath,
                                                                         @Nullable String rTxtOutDirOsPath,
                                                                         boolean optimizeRFile)
    throws IOException {
    final List<String> args = new ArrayList<String>();

    BuildToolInfo buildToolInfo = target.getBuildToolInfo();
    if (buildToolInfo == null) {
      return Collections.singletonMap(AndroidCompilerMessageKind.ERROR, Collections.singletonList("No Build Tools in the Android SDK."));
    }

    args.add(buildToolInfo.getPath(BuildToolInfo.PathId.AAPT));
    args.add("package");
    args.add("-m");

    if (nonConstantIds) {
      args.add("--non-constant-id");
    }

    if (resourceDirsOsPaths.length > 1) {
      args.add("--auto-add-overlay");
    }
    final Set<String> extraPackages = new HashSet<String>();

    for (Pair<String, String> pair : libRTxtFilesAndPackages) {
      extraPackages.add(pair.getSecond());
    }
    if (!extraPackages.isEmpty()) {
      args.add("--extra-packages");
      args.add(toPackagesString(ArrayUtil.toStringArray(extraPackages)));
    }

    if (customPackage != null) {
      args.add("--custom-package");
      args.add(customPackage);
    }

    if (rTxtOutDirOsPath != null) {
      args.add("--output-text-symbols");
      args.add(rTxtOutDirOsPath);
    }
    args.add("-J");
    args.add(outDirOsPath);
    args.add("-M");
    args.add(manifestFileOsPath);

    for (String libResFolderOsPath : resourceDirsOsPaths) {
      args.add("-S");
      args.add(libResFolderOsPath);
    }

    args.add("-I");
    args.add(target.getPath(IAndroidTarget.ANDROID_JAR));

    if (proguardCfgOutputFileOsPath != null) {
      args.add("-G");
      args.add(proguardCfgOutputFileOsPath);
    }
    final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(args));
    LOG.info(AndroidCommonUtils.command2string(args));

    if (!messages.get(AndroidCompilerMessageKind.ERROR).isEmpty()) {
      return messages;
    }

    if (optimizeRFile && !libRTxtFilesAndPackages.isEmpty() && rTxtOutDirOsPath != null) {
      final File rFile = new File(rTxtOutDirOsPath, SdkConstants.FN_RESOURCE_TEXT);
      // if the project has no resources the file could not exist.
      if (rFile.isFile()) {
        final SymbolLoader fullSymbolValues = new SymbolLoader(rFile);
        fullSymbolValues.load();
        final MultiMap<String, SymbolLoader> libMap = new MultiMap<String, SymbolLoader>();

        for (Pair<String, String> pair : libRTxtFilesAndPackages) {
          final File rTextFile = new File(pair.getFirst());
          final String libPackage = pair.getSecond();

          if (rTextFile.isFile()) {
            final SymbolLoader libSymbols = new SymbolLoader(rTextFile);
            libSymbols.load();
            libMap.putValue(libPackage, libSymbols);
          }
        }

        for (Map.Entry<String, Collection<SymbolLoader>> entry : libMap.entrySet()) {
          final String libPackage = entry.getKey();
          final Collection<SymbolLoader> symbols = entry.getValue();
          final SymbolWriter writer = new SymbolWriter(outDirOsPath, libPackage, fullSymbolValues);

          for (SymbolLoader symbolLoader : symbols) {
            writer.addSymbolsToWrite(symbolLoader);
          }
          writer.write();
        }
      }
    }
    return messages;
  }

  @NotNull
  private static String toPackagesString(@NotNull String[] packages) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0, n = packages.length; i < n; i++) {
      if (i > 0) {
        builder.append(':');
      }
      builder.append(packages[i]);
    }
    return builder.toString();
  }

  public static Map<AndroidCompilerMessageKind, List<String>> crunch(@NotNull IAndroidTarget target,
                                                                     @NotNull List<String> resPaths,
                                                                     @NotNull String outputPath) throws IOException {
    BuildToolInfo buildToolInfo = target.getBuildToolInfo();
    if (buildToolInfo == null) {
      return Collections.singletonMap(AndroidCompilerMessageKind.ERROR, Collections.singletonList("No Build Tools in the Android SDK."));
    }

    final ArrayList<String> args = new ArrayList<String>();

    args.add(buildToolInfo.getPath(BuildToolInfo.PathId.AAPT));

    args.add(COMMAND_CRUNCH);
    File tempDir = null;
    try {
      if (!resPaths.isEmpty()) {
        if (resPaths.size() == 1) {
          args.add("-S");
          args.add(resPaths.get(0));
        }
        else {
          tempDir = FileUtil.createTempDirectory("android_combined_resources", "tmp");

          for (int i = resPaths.size() - 1; i >= 0; i--) {
            final String resDirPath = resPaths.get(i);
            final File resDir = new File(resDirPath);

            if (resDir.exists()) {
              FileUtil.copyDir(resDir, tempDir, PNG_FILES_FILTER);
            }
          }
          args.add("-S");
          args.add(tempDir.getPath());
        }
      }

      args.add("-C");
      args.add(outputPath);

      LOG.info(AndroidCommonUtils.command2string(args));
      return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(args));
    }
    finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }

  public static Map<AndroidCompilerMessageKind, List<String>> packageResources(@NotNull IAndroidTarget target,
                                                                               int platformToolsRevision,
                                                                               @NotNull String manifestPath,
                                                                               @NotNull String[] resPaths,
                                                                               @NotNull String[] osAssetDirPaths,
                                                                               @NotNull String outputPath,
                                                                               @Nullable String configFilter,
                                                                               boolean debugMode,
                                                                               int versionCode,
                                                                               @Nullable String customManifestPackage,
                                                                               @Nullable String additionalParameters,
                                                                               FileFilter assetsFilter) throws IOException {
    for (String resDirPath : resPaths) {
      if (FileUtil.isAncestor(resDirPath, outputPath, false)) {
        throw new IOException("Resource directory " +
                              FileUtil.toSystemDependentName(resDirPath) +
                              " contains output " +
                              FileUtil.toSystemDependentName(outputPath));
      }
    }

    for (String assetsDirPath : osAssetDirPaths) {
      if (FileUtil.isAncestor(assetsDirPath, outputPath, false)) {
        throw new IOException("Assets directory " +
                              FileUtil.toSystemDependentName(assetsDirPath) +
                              " contains output " +
                              FileUtil.toSystemDependentName(outputPath));
      }
    }

    BuildToolInfo buildToolInfo = target.getBuildToolInfo();
    if (buildToolInfo == null) {
      return Collections.singletonMap(AndroidCompilerMessageKind.ERROR, Collections.singletonList("No Build Tools in the Android SDK."));
    }

    final ArrayList<String> args = new ArrayList<String>();

    args.add(buildToolInfo.getPath(BuildToolInfo.PathId.AAPT));

    args.add(COMMAND_PACKAGE);

    for (String path : resPaths) {
      args.add("-S");
      args.add(path);
    }

    args.add("-f");

    if (platformToolsRevision < 0 || platformToolsRevision > 7) {
      args.add("--no-crunch");
    }

    if (resPaths.length > 1) {
      args.add("--auto-add-overlay");
    }

    if (debugMode) {
      args.add("--debug-mode");
    }

    if (versionCode > 0) {
      args.add("--version-code");
      args.add(Integer.toString(versionCode));
    }

    if (configFilter != null) {
      args.add("-c");
      args.add(configFilter);
    }
    args.add("-M");
    args.add(manifestPath);

    File tempDir = null;
    try {
      if (osAssetDirPaths.length > 0) {
        final String[] nonEmptyAssetDirs = getNonEmptyExistingDirs(osAssetDirPaths);

        if (nonEmptyAssetDirs.length > 0) {
          if (nonEmptyAssetDirs.length == 1) {
            args.add("-A");
            args.add(nonEmptyAssetDirs[0]);
          }
          else {
            tempDir = FileUtil.createTempDirectory("android_combined_assets", "tmp");
            for (int i = nonEmptyAssetDirs.length - 1; i >= 0; i--) {
              final String assetDir = nonEmptyAssetDirs[i];
              FileUtil.copyDir(new File(assetDir), tempDir, assetsFilter);
            }
            args.add("-A");
            args.add(tempDir.getPath());
          }
        }
      }

      args.add("-I");
      args.add(target.getPath(IAndroidTarget.ANDROID_JAR));

      if (customManifestPackage != null) {
        args.add("--rename-manifest-package");
        args.add(customManifestPackage);
      }
      if (additionalParameters != null && !additionalParameters.isEmpty()) {
        args.addAll(ParametersListUtil.parse(additionalParameters));
      }
      args.add("-F");
      args.add(outputPath);
      LOG.info(AndroidCommonUtils.command2string(args));
      return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(args));
    }
    finally {
      if (tempDir != null) {
        FileUtil.delete(tempDir);
      }
    }
  }

  @NotNull
  private static String[] getNonEmptyExistingDirs(@NotNull String[] dirs) {
    final List<String> result = new ArrayList<String>();
    for (String dirPath : dirs) {
      final File dir = new File(dirPath);

      if (dir.isDirectory()) {
        final File[] children = dir.listFiles();

        if (children != null && children.length > 0) {
          result.add(dirPath);
        }
      }
    }
    return ArrayUtil.toStringArray(result);
  }


}