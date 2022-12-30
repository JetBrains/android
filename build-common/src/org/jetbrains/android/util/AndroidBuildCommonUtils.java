// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.util;

import com.android.SdkConstants;
import com.android.io.FileWrapper;
import com.android.repository.Revision;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.PkgProps;
import com.google.common.io.Files;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.execution.ParametersListUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import org.jetbrains.android.AndroidCommonBundle;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AndroidBuildCommonUtils {
  @NonNls public static final String PROGUARD_CFG_FILE_NAME = "proguard-project.txt";
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidCommonUtils");

  @NonNls public static final String MANIFEST_JAVA_FILE_NAME = "Manifest.java";
  @NonNls public static final String R_JAVA_FILENAME = "R.java";
  @NonNls public static final String CLASSES_JAR_FILE_NAME = "classes.jar";
  @NonNls public static final String AAR_DEPS_JAR_FILE_NAME = "aar_deps.jar";
  @NonNls public static final String CLASSES_FILE_NAME = "classes.dex";
  private static final Pattern WARNING_PATTERN = Pattern.compile(".*warning.*");
  private static final Pattern ERROR_PATTERN = Pattern.compile(".*error.*");
  private static final Pattern EXCEPTION_PATTERN = Pattern.compile(".*exception.*");

  public static final Pattern R_PATTERN = Pattern.compile("R(\\$.*)?\\.class");
  private static final Pattern MANIFEST_PATTERN = Pattern.compile("Manifest(\\$.*)?\\.class");
  private static final String BUILD_CONFIG_CLASS_NAME = "BuildConfig.class";

  public static final Pattern COMPILER_MESSAGE_PATTERN = Pattern.compile("(.+):(\\d+):.+");

  @NonNls public static final String PNG_EXTENSION = "png";
  private static final String[] DRAWABLE_EXTENSIONS = new String[]{PNG_EXTENSION, "jpg", "gif"};

  @NonNls public static final String RELEASE_BUILD_OPTION = "RELEASE_BUILD_KEY";
  @NonNls public static final String PROGUARD_CFG_PATHS_OPTION = "ANDROID_PROGUARD_CFG_PATHS";
  @NonNls public static final String PROGUARD_OUTPUT_JAR_NAME = "obfuscated_sources.jar";
  @NonNls public static final String SYSTEM_PROGUARD_CFG_FILE_NAME = "proguard-android.txt";
  @NonNls private static final String PROGUARD_HOME_ENV_VARIABLE = "PROGUARD_HOME";

  @NonNls public static final String ADDITIONAL_NATIVE_LIBS_ELEMENT = "additionalNativeLibs";

  private static final String[] TEST_CONFIGURATION_TYPE_IDS =
    {"AndroidJUnit", "JUnit", "TestNG", "ScalaTestRunConfiguration", "SpecsRunConfiguration", "Specs2RunConfiguration"};
  @NonNls public static final String ANNOTATIONS_JAR_RELATIVE_PATH = "/tools/support/annotations.jar";

  @NonNls public static final String PACKAGE_MANIFEST_ATTRIBUTE = "package";

  @NonNls public static final String ANDROID_FINAL_PACKAGE_FOR_ARTIFACT_SUFFIX = ".afp";
  @NonNls public static final String PROGUARD_CFG_OUTPUT_FILE_NAME = "proguard.txt";

  @NonNls public static final String MANIFEST_MERGING_BUILD_TARGET_TYPE_ID = "android-manifest-merging";
  @NonNls public static final String AAR_DEPS_BUILD_TARGET_TYPE_ID = "android-aar-deps";
  @NonNls public static final String DEX_BUILD_TARGET_TYPE_ID = "android-dex";
  @NonNls public static final String PRE_DEX_BUILD_TARGET_TYPE_ID = "android-pre-dex";
  @NonNls public static final String PACKAGING_BUILD_TARGET_TYPE_ID = "android-packaging";
  @NonNls public static final String RESOURCE_CACHING_BUILD_TARGET_ID = "android-resource-caching";
  @NonNls public static final String RESOURCE_PACKAGING_BUILD_TARGET_ID = "android-resource-packaging";
  @NonNls public static final String LIBRARY_PACKAGING_BUILD_TARGET_ID = "android-library-packaging";
  @NonNls public static final String AUTOGENERATED_JAVA_FILE_HEADER = "/*___Generated_by_IDEA___*/";

  /** Android Test Run Configuration Type Id, defined here so as to be accessible to both JPS and Android plugin. */
  @NonNls public static final String ANDROID_TEST_RUN_CONFIGURATION_TYPE = "AndroidTestRunConfigurationType";

  private AndroidBuildCommonUtils() {
  }

  public static boolean isTestConfiguration(@NotNull String typeId) {
    return ArrayUtilRt.find(TEST_CONFIGURATION_TYPE_IDS, typeId) >= 0;
  }

  public static boolean isInstrumentationTestConfiguration(@NotNull String typeId) {
    return ANDROID_TEST_RUN_CONFIGURATION_TYPE.equals(typeId);
  }

  public static String command2string(@NotNull Collection<String> command) {
    StringBuilder builder = new StringBuilder();
    for (Iterator<String> it = command.iterator(); it.hasNext(); ) {
      String s = it.next();
      builder.append('[');
      builder.append(s);
      builder.append(']');
      if (it.hasNext()) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  public static void moveAllFiles(@NotNull File from, @NotNull File to, @NotNull Collection<File> newFiles) throws IOException {
    if (from.isFile()) {
      FileUtil.rename(from, to);
      newFiles.add(to);
    }
    else {
      File[] children = from.listFiles();

      if (children != null) {
        for (File child : children) {
          moveAllFiles(child, new File(to, child.getName()), newFiles);
        }
      }
    }
  }

  public static void handleDexCompilationResult(@NotNull Process process,
                                                @NotNull String commandLine,
                                                @NotNull String outputFilePath,
                                                @NotNull Map<AndroidCompilerMessageKind, List<String>> messages, boolean multiDex) {
    BaseOSProcessHandler handler = new BaseOSProcessHandler(process, commandLine, null);
    handler.addProcessListener(new ProcessAdapter() {
      private AndroidCompilerMessageKind myCategory = null;

      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        String[] msgs = event.getText().split("\\n");
        for (String msg : msgs) {
          msg = msg.trim();
          String msglc = msg.toLowerCase(Locale.US);
          if (outputType == ProcessOutputTypes.STDERR) {
            if (WARNING_PATTERN.matcher(msglc).matches()) {
              myCategory = AndroidCompilerMessageKind.WARNING;
            }
            if (ERROR_PATTERN.matcher(msglc).matches() || EXCEPTION_PATTERN.matcher(msglc).matches() || myCategory == null) {
              myCategory = AndroidCompilerMessageKind.ERROR;
            }
            messages.get(myCategory).add(msg);
          }
          else if (outputType == ProcessOutputTypes.STDOUT) {
            if (!msglc.startsWith("processing")) {
              messages.get(AndroidCompilerMessageKind.INFORMATION).add(msg);
            }
          }

          LOG.debug(msg);
        }
      }
    });

    handler.startNotify();
    handler.waitFor();

    List<String> errors = messages.get(AndroidCompilerMessageKind.ERROR);

    if (new File(outputFilePath).isFile()) {
      // if compilation finished correctly, show all errors as warnings
      messages.get(AndroidCompilerMessageKind.WARNING).addAll(errors);
      errors.clear();
    }
    else if (errors.isEmpty() && !multiDex) {
      errors.add("Cannot create classes.dex file");
    }
  }

  @NotNull
  public static List<String> packClassFilesIntoJar(@NotNull String[] firstPackageDirPaths,
                                                 @NotNull String[] libFirstPackageDirPaths,
                                                 @NotNull File jarFile) throws IOException {
    List<Pair<File, String>> files = new ArrayList<>();
    for (String path : firstPackageDirPaths) {
      File firstPackageDir = new File(path);
      if (firstPackageDir.exists()) {
        packClassFilesIntoJar(firstPackageDir, firstPackageDir.getParentFile(), true, files);
      }
    }

    for (String path : libFirstPackageDirPaths) {
      File firstPackageDir = new File(path);
      if (firstPackageDir.exists()) {
        packClassFilesIntoJar(firstPackageDir, firstPackageDir.getParentFile(), false, files);
      }
    }

    if (!files.isEmpty()) {
      JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile));
      try {
        for (Pair<File, String> pair : files) {
          packIntoJar(jos, pair.getFirst(), pair.getSecond());
        }
      }
      finally {
        jos.close();
      }
    }
    else if (jarFile.isFile()) {
      if (!jarFile.delete()) {
        throw new IOException("Cannot delete file " + FileUtilRt.toSystemDependentName(jarFile.getPath()));
      }
    }
    List<String> srcFiles = new ArrayList<>();

    for (Pair<File, String> pair : files) {
      srcFiles.add(pair.getFirst().getPath());
    }
    return srcFiles;
  }

  private static void packClassFilesIntoJar(@NotNull File file,
                                            @NotNull File rootDirectory,
                                            boolean packRAndManifestClasses,
                                            @NotNull List<Pair<File, String>> files) {
    if (file.isDirectory()) {
      File[] children = file.listFiles();

      if (children != null) {
        for (File child : children) {
          packClassFilesIntoJar(child, rootDirectory, packRAndManifestClasses, files);
        }
      }
    }
    else if (file.isFile()) {
      if (!FileUtilRt.extensionEquals(file.getName(), "class")) {
        return;
      }

      if (!packRAndManifestClasses &&
          (R_PATTERN.matcher(file.getName()).matches() ||
           MANIFEST_PATTERN.matcher(file.getName()).matches() ||
           BUILD_CONFIG_CLASS_NAME.equals(file.getName()))) {
        return;
      }

      String rootPath = rootDirectory.getAbsolutePath();

      String path = file.getAbsolutePath();
      path = FileUtil.toSystemIndependentName(path.substring(rootPath.length()));
      if (path.charAt(0) == '/') {
        path = path.substring(1);
      }

      files.add(Pair.create(file, path));
    }
  }

  public static void packIntoJar(@NotNull JarOutputStream jar, @NotNull File file, @NotNull String path) throws IOException {
    JarEntry entry = new JarEntry(path);
    entry.setTime(file.lastModified());
    jar.putNextEntry(entry);

    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
      byte[] buffer = new byte[1024];
      int count;
      while ((count = bis.read(buffer)) != -1) {
        jar.write(buffer, 0, count);
      }
      jar.closeEntry();
    }
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> launchProguard(@NotNull IAndroidTarget target,
                                                                             int sdkToolsRevision,
                                                                             @NotNull String sdkOsPath,
                                                                             @NotNull String javaExecutablePath,
                                                                             @NotNull String proguardVmOptions,
                                                                             @NotNull String[] proguardConfigFileOsPaths,
                                                                             @NotNull String inputJarOsPath,
                                                                             @NotNull String[] externalJarOsPaths,
                                                                             @NotNull String[] providedJarOsPaths,
                                                                             @NotNull String outputJarFileOsPath,
                                                                             @Nullable String logDirOutputOsPath) throws IOException {
    List<String> commands = new ArrayList<>();
    commands.add(javaExecutablePath);

    if (!proguardVmOptions.isEmpty()) {
      commands.addAll(ParametersListUtil.parse(proguardVmOptions));
    }
    commands.add("-jar");
    String proguardHome = getProguardHomeDirOsPath(sdkOsPath);
    String proguardJarOsPath = proguardHome + File.separator + "lib" + File.separator + "proguard.jar";
    commands.add(proguardJarOsPath);

    if (isIncludingInProguardSupported(sdkToolsRevision)) {
      for (String proguardConfigFileOsPath : proguardConfigFileOsPaths) {
        commands.add("-include");
        commands.add(quotePath(proguardConfigFileOsPath));
      }
    }
    else {
      commands.add("@" + quotePath(proguardConfigFileOsPaths[0]));
    }

    commands.add("-injars");

    StringBuilder builder = new StringBuilder(quotePath(inputJarOsPath));

    for (String jarFile : externalJarOsPaths) {
      builder.append(File.pathSeparatorChar);
      builder.append(quotePath(jarFile));
    }
    commands.add(builder.toString());

    commands.add("-outjars");
    commands.add(quotePath(outputJarFileOsPath));

    commands.add("-libraryjars");

    builder = new StringBuilder(quotePath(target.getPath(IAndroidTarget.ANDROID_JAR).toString()));

    List<OptionalLibrary> libraries = target.getAdditionalLibraries();
    for (OptionalLibrary lib : libraries) {
      builder.append(File.pathSeparatorChar);
      builder.append(quotePath(lib.getJar().toAbsolutePath().toString()));
    }
    for (String path : providedJarOsPaths) {
      builder.append(File.pathSeparatorChar);
      builder.append(quotePath(path));
    }
    commands.add(builder.toString());

    if (logDirOutputOsPath != null) {
      commands.add("-dump");
      commands.add(quotePath(new File(logDirOutputOsPath, "dump.txt").getAbsolutePath()));

      commands.add("-printseeds");
      commands.add(quotePath(new File(logDirOutputOsPath, "seeds.txt").getAbsolutePath()));

      commands.add("-printusage");
      commands.add(quotePath(new File(logDirOutputOsPath, "usage.txt").getAbsolutePath()));

      commands.add("-printmapping");
      commands.add(quotePath(new File(logDirOutputOsPath, "mapping.txt").getAbsolutePath()));
    }

    LOG.info(command2string(commands));
    Map<String, String> home = System.getenv().containsKey(PROGUARD_HOME_ENV_VARIABLE)
                                     ? Collections.emptyMap()
                                     : Collections.singletonMap(PROGUARD_HOME_ENV_VARIABLE, proguardHome);
    return AndroidExecutionUtil.doExecute(ArrayUtilRt.toStringArray(commands), home);
  }

  @NotNull
  public static String getProguardHomeDirOsPath(@NotNull String sdkOsPath) {
    return sdkOsPath + File.separator + SdkConstants.FD_TOOLS + File.separator + SdkConstants.FD_PROGUARD;
  }

  private static String quotePath(String path) {
    if (path.indexOf(' ') >= 0) {
      path = '\'' + path + '\'';
    }
    return path;
  }

  public static String buildTempInputJar(@NotNull String[] classFilesDirOsPaths, @NotNull String[] libClassFilesDirOsPaths)
    throws IOException {
    File inputJar = FileUtil.createTempFile("proguard_input", ".jar");

    packClassFilesIntoJar(classFilesDirOsPaths, libClassFilesDirOsPaths, inputJar);

    return FileUtilRt.toSystemDependentName(inputJar.getPath());
  }

  public static String platformToolPath(@NotNull String toolFileName) {
    return SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + toolFileName;
  }

  public static boolean isIncludingInProguardSupported(int sdkToolsRevision) {
    return sdkToolsRevision == -1 || sdkToolsRevision >= 17;
  }

  /**
   * Gets the {@link Revision} for the given package in the given SDK from the {@code source.properties} file.
   *
   * @return The {@link Revision}, or {@code null} if the {@code source.properties} file doesn't exist, doesn't contain a revision, or
   * the revision is unparsable.
   */
  @Nullable
  public static Revision parsePackageRevision(@NotNull String sdkDirOsPath, @NotNull String packageDirName) {
    File propFile =
      new File(sdkDirOsPath + File.separatorChar + packageDirName + File.separatorChar + SdkConstants.FN_SOURCE_PROP);
    if (propFile.exists() && propFile.isFile()) {
      Map<String, String> map =
        ProjectProperties.parsePropertyFile(new FileWrapper(propFile), new MessageBuildingSdkLog());
      if (map == null) {
        return null;
      }
      String revision = map.get(PkgProps.PKG_REVISION);

      if (revision != null) {
        return Revision.parseRevision(revision);
      }
    }
    return null;
  }

  @NotNull
  public static String readFile(@NotNull File file) throws IOException {
    return Files.toString(file, StandardCharsets.UTF_8);
  }

  public static boolean contains2Identifiers(String packageName) {
    return packageName.split("\\.").length >= 2;
  }

  public static boolean directoriesContainSameContent(@NotNull File dir1, @NotNull File dir2, @Nullable FileFilter filter)
    throws IOException {
    if (dir1.exists() != dir2.exists()) {
      return false;
    }

    File[] children1 = getFilteredChildren(dir1, filter);
    File[] children2 = getFilteredChildren(dir2, filter);

    if (children1 == null || children2 == null) {
      return Arrays.equals(children1, children2);
    }

    if (children1.length != children2.length) {
      return false;
    }

    for (int i = 0; i < children1.length; i++) {
      File child1 = children1[i];
      File child2 = children2[i];

      if (!Objects.equals(child1.getName(), child2.getName())) {
        return false;
      }

      boolean childDir = child1.isDirectory();
      if (childDir != child2.isDirectory()) {
        return false;
      }

      if (childDir) {
        if (!directoriesContainSameContent(child1, child2, filter)) {
          return false;
        }
      }
      else {
        String content1 = readFile(child1);
        String content2 = readFile(child2);

        if (!Objects.equals(content1, content2)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  private static File[] getFilteredChildren(@NotNull File dir, @Nullable FileFilter filter) {
    File[] children = dir.listFiles();
    if (children == null || children.length == 0 || filter == null) {
      return children;
    }

    List<File> result = new ArrayList<>();
    for (File child : children) {
      if (child.isDirectory() || filter.accept(child)) {
        result.add(child);
      }
    }
    return result.toArray(ArrayUtil.EMPTY_FILE_ARRAY);
  }

  @NotNull
  public static String addSuffixToFileName(@NotNull String path, @NotNull String suffix) {
    int dot = path.lastIndexOf('.');
    if (dot < 0) {
      return path + suffix;
    }
    String a = path.substring(0, dot);
    String b = path.substring(dot);
    return a + suffix + b;
  }

  public static void signApk(@NotNull File srcApk,
                             @NotNull File destFile,
                             @NotNull PrivateKey privateKey,
                             @NotNull X509Certificate certificate)
    throws IOException {
    try (SafeSignedJarBuilder builder = new SafeSignedJarBuilder(privateKey, certificate, destFile.getPath())){
      builder.writeZip(srcApk, null);
    }
  }

  public static boolean hasXmxParam(@NotNull List<String> parameters) {
    for (String param : parameters) {
      if (param.startsWith("-Xmx")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String executeZipAlign(@NotNull String zipAlignPath, @NotNull File source, @NotNull File destination) {
    List<String> commandLine = Arrays.asList(zipAlignPath, "-f", "4", source.getAbsolutePath(), destination.getAbsolutePath());
    ProcessBuilder processBuilder = new ProcessBuilder(commandLine);

    BaseOSProcessHandler handler;
    try {
      handler = new BaseOSProcessHandler(processBuilder.start(), StringUtil.join(commandLine, " "), null);
    }
    catch (IOException e) {
      return e.getMessage();
    }
    StringBuilder builder = new StringBuilder();
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        builder.append(event.getText());
      }
    });
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return exitCode != 0 ? builder.toString() : null;
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> buildArtifact(@NotNull String artifactName,
                                                                            @NotNull String messagePrefix,
                                                                            @NotNull IAndroidTarget target,
                                                                            @Nullable String artifactFilePath,
                                                                            @NotNull String keyStorePath,
                                                                            @Nullable String keyAlias,
                                                                            @Nullable String keyStorePassword,
                                                                            @Nullable String keyPassword)
    throws GeneralSecurityException, IOException {
    Map<AndroidCompilerMessageKind, List<String>> messages = new HashMap<>();
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<>());

    Pair<PrivateKey, X509Certificate> pair = getPrivateKeyAndCertificate(messagePrefix, messages, keyAlias, keyStorePath,
                                                                               keyStorePassword, keyPassword);
    if (pair == null) {
      return messages;
    }
    String prefix = "Cannot sign artifact " + artifactName + ": ";

    if (artifactFilePath == null) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(prefix + "output path is not specified");
      return messages;
    }

    File artifactFile = new File(artifactFilePath);
    if (!artifactFile.exists()) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(prefix + "file " + artifactFilePath + " hasn't been generated");
      return messages;
    }
    String zipAlignPath = getZipAlign(target);

    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("android_artifact", "tmp");
      File tmpArtifact = new File(tmpDir, "tmpArtifact.apk");

      signApk(artifactFile, tmpArtifact, pair.getFirst(), pair.getSecond());

      if (!FileUtil.delete(artifactFile)) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Cannot delete file " + artifactFile.getPath());
        return messages;
      }

      if (zipAlignPath != null) {
        String errorMessage = executeZipAlign(zipAlignPath, tmpArtifact, artifactFile);
        if (errorMessage != null) {
          messages.get(AndroidCompilerMessageKind.ERROR).add(messagePrefix + "zip-align: " + errorMessage);
          return messages;
        }
      }
      else {
        messages.get(AndroidCompilerMessageKind.WARNING).add(messagePrefix + AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.zip.align.error"));
        FileUtil.copy(tmpArtifact, artifactFile);
      }
    }
    finally {
      if (tmpDir != null) {
        FileUtil.delete(tmpDir);
      }
    }
    return messages;
  }

  @Nullable
  private static Pair<PrivateKey, X509Certificate> getPrivateKeyAndCertificate(@NotNull String errorPrefix,
                                                                               @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                                                               @Nullable String keyAlias,
                                                                               @Nullable String keyStoreFilePath,
                                                                               @Nullable String keyStorePasswordStr,
                                                                               @Nullable String keyPasswordStr)
    throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableEntryException {

    if (keyStoreFilePath == null || keyStoreFilePath.isEmpty()) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key store file is not specified");
      return null;
    }
    if (keyStorePasswordStr == null) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key store password is not specified");
      return null;
    }
    if (keyAlias == null || keyAlias.isEmpty()) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key alias is not specified");
      return null;
    }
    if (keyPasswordStr == null) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key password is not specified");
      return null;
    }
    File keyStoreFile = new File(keyStoreFilePath);
    char[] keystorePassword = keyStorePasswordStr.toCharArray();
    char[] plainKeyPassword = keyPasswordStr.toCharArray();

    KeyStore keyStore;
    InputStream is = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      is = new FileInputStream(keyStoreFile);
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(is, keystorePassword);

      KeyStore.PrivateKeyEntry entry =
        (KeyStore.PrivateKeyEntry)keyStore.getEntry(keyAlias, new KeyStore.PasswordProtection(plainKeyPassword));
      if (entry == null) {
        messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.key.error", keyAlias));
        return null;
      }

      PrivateKey privateKey = entry.getPrivateKey();
      Certificate certificate = entry.getCertificate();
      if (privateKey == null || certificate == null) {
        messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.key.error", keyAlias));
        return null;
      }
      return Pair.create(privateKey, (X509Certificate)certificate);
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  @Nullable
  public static String getZipAlign(@NotNull IAndroidTarget target) {
    BuildToolInfo buildToolInfo = target.getBuildToolInfo();

    if (buildToolInfo != null) {
      String path = null;
      try {
        path = buildToolInfo.getPath(BuildToolInfo.PathId.ZIP_ALIGN);
      }
      catch (Throwable ignored) {
        return null;
      }
      if (path != null && new File(path).exists()) {
        return path;
      }
    }
    return null;
  }
}
