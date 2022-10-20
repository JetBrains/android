/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.gradle.adtimport;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.BIN_FOLDER;
import static com.android.SdkConstants.DOT_AIDL;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_FS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_RS;
import static com.android.SdkConstants.DOT_RSH;
import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JAVA;
import static com.android.SdkConstants.FD_JAVA_RES;
import static com.android.SdkConstants.FD_MAIN;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_SOURCES;
import static com.android.SdkConstants.FD_TEST;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.FN_PROJECT_PROPERTIES;
import static com.android.SdkConstants.GEN_FOLDER;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.tools.idea.gradle.adtimport.GradleImport.ECLIPSE_DOT_CLASSPATH;
import static com.android.tools.idea.gradle.adtimport.GradleImport.ECLIPSE_DOT_PROJECT;
import static com.android.tools.idea.gradle.adtimport.GradleImport.isIgnoredFile;
import static com.android.tools.idea.gradle.adtimport.GradleImport.isTextFile;
import static com.android.tools.idea.gradle.util.ImportUtil.APPCOMPAT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.GRIDLAYOUT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.MEDIA_ROUTER_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.SUPPORT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.SUPPORT_GROUP_ID;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public abstract class ImportModule implements Comparable<ImportModule> {
  @SuppressWarnings("SpellCheckingInspection")
  private static final String SHERLOCK_DEP = "com.actionbarsherlock:actionbarsherlock:4.4.0@aar";
  private static final String PLAY_SERVICES_DEP = "com.google.android.gms:play-services:+";

  protected final GradleImport myImporter;
  protected final List<GradleCoordinate> myDependencies = new ArrayList<>();
  protected final List<GradleCoordinate> myTestDependencies = new ArrayList<>();
  protected final List<File> myJarDependencies = new ArrayList<>();
  protected final List<File> myTestJarDependencies = new ArrayList<>();
  protected List<GradleCoordinate> myReplaceWithDependencies;
  private String myModuleName;

  public ImportModule(@NonNull GradleImport importer) {
    myImporter = importer;
  }

  protected static File getTestJarOutputRelativePath(File jar) {
    return new File(LIBS_FOLDER, jar.getName());
  }

  private static void recordCopiedFile(@NonNull Set<File> copied, @NonNull File file) throws IOException {
    copied.add(file);
    copied.add(file.getCanonicalFile());
  }

  protected abstract boolean isLibrary();
  protected abstract boolean isApp();
  protected abstract boolean isAndroidLibrary();
  protected abstract boolean isAndroidProject();
  protected abstract boolean isJavaLibrary();
  protected abstract boolean isNdkProject();

  @NonNull
  protected abstract AndroidVersion getCompileSdkVersion();

  @NonNull
  protected abstract AndroidVersion getMinSdkVersion();

  @NonNull
  protected abstract AndroidVersion getTargetSdkVersion();

  @Nullable
  protected abstract String getAddOn();

  @NonNull
  public abstract File getDir();

  @NonNull
  protected abstract String getOriginalName();

  @NonNull
  protected abstract List<File> getSourcePaths();

  @NonNull
  protected abstract List<File> getJarPaths();

  @NonNull
  protected abstract List<File> getTestJarPaths();

  @NonNull
  protected abstract List<File> getNativeLibs();

  @NonNull
  protected abstract File resolveFile(@NonNull File file);

  @NonNull
  protected abstract File getCanonicalModuleDir();

  @NonNull
  protected abstract List<File> getLocalProguardFiles();

  @NonNull
  protected abstract List<File> getSdkProguardFiles();

  @NonNull
  protected abstract String getLanguageLevel();

  @NonNull
  protected abstract List<ImportModule> getDirectDependencies();

  @NonNull
  protected abstract List<ImportModule> getAllDependencies();

  @Nullable
  protected abstract String getPackage();

  @Nullable
  protected abstract File getLintXml();

  @Nullable
  protected abstract File getOutputDir();

  @Nullable
  protected abstract File getManifestFile();

  @Nullable
  protected abstract File getResourceDir();

  @Nullable
  protected abstract File getAssetsDir();

  @Nullable
  protected abstract File getNativeSources();

  @Nullable
  protected abstract String getNativeModuleName();

  @Nullable
  protected abstract File getInstrumentationDir();

  @Nullable
  protected abstract Charset getFileEncoding(@NonNull File file);

  @Nullable
  protected abstract Charset getProjectEncoding(@NonNull File file);

  public void initialize() {
    initDependencies();
    initReplaceWithDependency();
  }

  protected void initDependencies() {
  }

  @Nullable
  public GradleCoordinate getLatestVersion(String artifact) {
    int compileVersion = GradleImport.CURRENT_COMPILE_VERSION;
    AndroidVersion version = getCompileSdkVersion();
    if (version != AndroidVersion.DEFAULT) {
      compileVersion = version.getFeatureLevel();
    }

    // If you're using for example android-14, you still need support libraries
    // from version 18 (earliest version where we have all the libs in the m2 repository)
    if (compileVersion < 18) {
      compileVersion = 18;
    }

    int requiredVersion = compileVersion;
    String max =
      RepositoryUrlManager.get().getLibraryRevision(SUPPORT_GROUP_ID, artifact,
                                                    (v) -> v.getMajor() == requiredVersion, true,
                                                    FileSystems.getDefault());
    if (max != null) {
      return GradleCoordinate.parseCoordinateString(SUPPORT_GROUP_ID + ':' + artifact + ':' + max);
    }

    String coordinate = SUPPORT_GROUP_ID + ':' + artifact + ':' + compileVersion + ".+";
    return GradleCoordinate.parseCoordinateString(coordinate);
  }

  @Nullable
  protected GradleCoordinate getAppCompatDependency() {
    return getLatestVersion(APPCOMPAT_ARTIFACT);
  }

  @Nullable
  protected GradleCoordinate getSupportLibDependency() {
    return getLatestVersion(SUPPORT_ARTIFACT);
  }

  @Nullable
  protected GradleCoordinate getGridLayoutDependency() {
    return getLatestVersion(GRIDLAYOUT_ARTIFACT);
  }

  @Nullable
  protected GradleCoordinate getMediaRouterDependency() {
    return getLatestVersion(MEDIA_ROUTER_ARTIFACT);
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Nullable
  GradleCoordinate guessDependency(@NonNull File jar) {
    // Make guesses based on library. For now, we do simple name checks, but
    // later consider looking at jar contents, md5 sums etc, especially to
    // pick up exact version numbers of popular libraries.
    // (This list was generated by just looking at some existing projects
    // and seeing which .jar files they depended on and then consulting available
    // gradle dependencies via http://gradleplease.appspot.com/ )
    String name = StringUtil.toLowerCase(jar.getName());
    if (name.equals("android-support-v4.jar")) {
      myImporter.markJarHandled(jar);
      return getSupportLibDependency();
    }
    else if (name.equals("android-support-v7-gridlayout.jar")) {
      myImporter.markJarHandled(jar);
      return getGridLayoutDependency();
    }
    else if (name.equals("android-support-v7-appcompat.jar")) {
      myImporter.markJarHandled(jar);
      return getAppCompatDependency();
    }
    else if (name.equals("com_actionbarsherlock.jar") || name.equalsIgnoreCase("actionbarsherlock.jar")) {
      myImporter.markJarHandled(jar);
      return GradleCoordinate.parseCoordinateString(SHERLOCK_DEP);
    }
    else if (name.equals("guava.jar") || name.startsWith("guava-")) {
      myImporter.markJarHandled(jar);
      String version = getVersion(jar, "guava-", name, "18.0");
      if (version.startsWith("r")) { // really old versions
        version = "15.0";
      }
      return GradleCoordinate.parseCoordinateString("com.google.guava:guava:" + version);
    }
    else if (name.startsWith("joda-time")) {
      myImporter.markJarHandled(jar);
      // Convert joda-time-2.1 jar into joda-time:joda-time:2.1 etc
      String version = getVersion(jar, "joda-time-", name, "2.7");
      return GradleCoordinate.parseCoordinateString("joda-time:joda-time:" + version);
    }
    else if (name.startsWith("robotium-solo-")) {
      myImporter.markJarHandled(jar);
      String version = getVersion(jar, "robotium-solo-", name, "5.3.1");
      return GradleCoordinate.parseCoordinateString("com.jayway.android.robotium:robotium-solo:" + version);
    }
    else if (name.startsWith("protobuf-java-")) {
      myImporter.markJarHandled(jar);
      String version = getVersion(jar, "protobuf-java-", name, "2.6.1");
      return GradleCoordinate.parseCoordinateString("com.google.protobuf:protobuf-java:" + version);
    }
    else if (name.startsWith("gson-")) {
      myImporter.markJarHandled(jar);
      String version = getVersion(jar, "gson-", name, "2.3.1");
      return GradleCoordinate.parseCoordinateString("com.google.code.gson:gson:" + version);
    }
    else if (name.startsWith("google-http-client-gson-")) {
      myImporter.markJarHandled(jar);
      return GradleCoordinate.parseCoordinateString("com.google.http-client:google-http-client-gson:1.20.0");
    }
    else if (name.startsWith("svg-android")) {
      myImporter.markJarHandled(jar);
      return GradleCoordinate.parseCoordinateString("com.github.japgolly.android:svg-android:2.0.6");
    }
    else if (name.equals("gcm.jar")) {
      myImporter.markJarHandled(jar);
      return GradleCoordinate.parseCoordinateString(PLAY_SERVICES_DEP);
    }

    // TODO: Consider other libraries if and when they get Gradle dependencies:
    // analytics, volley, ...

    return null;
  }

  private String getVersion(File jar, String prefix, String jarName, String defaultVersion) {
    if (jarName.matches(prefix + "([\\d\\.]+)\\.jar")) {
      String version = jarName.substring(prefix.length(), jarName.length() - 4);
      if (!defaultVersion.equals(version)) {
        myImporter.getSummary().reportGuessedVersion(jar);
      }
      return version;
    }

    return defaultVersion;
  }

  /**
   * See if this is a library that looks like a known dependency; if so, just
   * use a dependency instead of the library
   */
  @SuppressWarnings("SpellCheckingInspection")
  private void initReplaceWithDependency() {
    if (isLibrary() && myImporter.isReplaceLibs()) {
      String pkg = getPackage();
      if (pkg != null) {
        if (pkg.equals("com.actionbarsherlock")) {
          myReplaceWithDependencies = Arrays.asList(GradleCoordinate.parseCoordinateString(SHERLOCK_DEP), getSupportLibDependency());
        }
        else if (pkg.equals("android.support.v7.gridlayout")) {
          myReplaceWithDependencies = Collections.singletonList(getGridLayoutDependency());
        }
        else if (pkg.equals("com.google.android.gms")) {
          myReplaceWithDependencies = Collections.singletonList(GradleCoordinate.parseCoordinateString(PLAY_SERVICES_DEP));
        }
        else if (pkg.equals("android.support.v7.appcompat")) {
          myReplaceWithDependencies = Collections.singletonList(getAppCompatDependency());
        }
        else if (pkg.equals("android.support.v7.mediarouter")) {
          myReplaceWithDependencies = Collections.singletonList(getMediaRouterDependency());
        }

        if (myReplaceWithDependencies != null) {
          myImporter.getSummary().reportReplacedLib(getOriginalName(), myReplaceWithDependencies);
        }
      }
    }
  }

  public boolean isReplacedWithDependency() {
    return myReplaceWithDependencies != null && !myReplaceWithDependencies.isEmpty();
  }

  public List<GradleCoordinate> getReplaceWithDependencies() {
    return myReplaceWithDependencies;
  }

  public String getModuleName() {
    if (myModuleName == null) {
      if (myImporter.isGradleNameStyle() && !myImporter.isImportIntoExisting() && myImporter.getModuleCount() == 1) {
        myModuleName = "app";
        return myModuleName;
      }

      String string = getOriginalName();
      // Strip whitespace and characters which can pose a problem when the module
      // name is referenced as a module name in Gradle (Groovy) files
      StringBuilder sb = new StringBuilder(string.length());
      for (int i = 0, n = string.length(); i < n; i++) {
        char c = string.charAt(i);
        if (Character.isJavaIdentifierPart(c)) {
          sb.append(c);
        }
      }

      String moduleName = sb.toString();
      if (!moduleName.isEmpty() && !Character.isJavaIdentifierStart(moduleName.charAt(0))) {
        moduleName = '_' + moduleName;
      }

      if (myImporter.isGradleNameStyle() && !moduleName.isEmpty()) {
        moduleName = Character.toLowerCase(moduleName.charAt(0)) + moduleName.substring(1);
      }
      myModuleName = moduleName;
    }
    return myModuleName;
  }

  public void setModuleName(String name) {
    myModuleName = name;
  }

  public void pickUniqueName(@NonNull File projectDir) {
    assert projectDir.exists() : projectDir;
    String preferredName = getModuleName();

    // If the name ends with a number, strip that off and increment from it.
    // In other words if the module name is "foo", we test "foo2", "foo3", and so on.
    // But if the name already is "module49", we don't do "module492", ... we try "module50"
    int length = preferredName.length();
    int lastDigit = length;
    for (int i = length - 1; i >= 1; i--) { // 1: name cannot start with a digit!
      if (!Character.isDigit(preferredName.charAt(i))) {
        break;
      }
      else {
        lastDigit = i;
      }
    }
    int startingNumber = 2;
    if (lastDigit < length) {
      startingNumber = Integer.parseInt(preferredName.substring(lastDigit)) + 1;
      preferredName = preferredName.substring(0, lastDigit);
    }

    for (int i = startingNumber; ; i++) {
      String name = preferredName + i;
      if (!(new File(projectDir, name)).exists()) {
        myModuleName = name;
        break;
      }
    }
  }

  public String getModuleReference() {
    String moduleName = getModuleName();
    File file = new File(moduleName);
    StringBuilder builder = new StringBuilder(moduleName.length() + 1);
    while (file != null) {
      builder.insert(0, file.getName());
      builder.insert(0, ':');
      file = file.getParentFile();
    }
    return builder.toString();
  }

  protected File getJarOutputRelativePath(File jar) {
    if (jar.isAbsolute()) {
      File relative;
      try {
        relative = GradleImport.computeRelativePath(getCanonicalModuleDir(), jar);
      }
      catch (IOException ioe) {
        relative = null;
      }
      if (relative != null) {
        jar = relative;
      }
      else {
        jar = new File(LIBS_FOLDER, jar.getName());
      }
    }

    return jar;
  }

  public void copyInto(@NonNull File destDir) throws IOException {
    ImportSummary summary = myImporter.getSummary();

    Set<File> copied = Sets.newHashSet();

    final File main = new File(destDir, FD_SOURCES + separator + FD_MAIN);
    myImporter.mkdirs(main);
    if (isAndroidProject()) {
      File srcManifest = getManifestFile();
      if (srcManifest != null && srcManifest.exists()) {
        File destManifest = new File(main, ANDROID_MANIFEST_XML);
        myImporter.copyTextFile(this, srcManifest, destManifest);
        summary.reportMoved(this, srcManifest, destManifest);
        recordCopiedFile(copied, srcManifest);
      }
      File srcRes = getResourceDir();
      if (srcRes != null && srcRes.exists()) {
        File destRes = new File(main, FD_RES);
        myImporter.mkdirs(destRes);
        myImporter.copyDir(srcRes, destRes, new GradleImport.CopyHandler() {
          @Override
          public boolean handle(@NonNull File source, @NonNull File dest, boolean updateEncoding, @Nullable ImportModule sourceModule)
              throws IOException {
            // Resource files in non-value folders should use only lower case characters
            if (hasUpperCaseExtension(dest) && !isIgnoredFile(source)) {
              File parentFile = source.getParentFile();
              if (parentFile != null) {
                ResourceFolderType folderType = ResourceFolderType.getFolderType(parentFile.getName());
                if (folderType != ResourceFolderType.VALUES) {
                  String name = dest.getName();
                  int dot = name.indexOf('.');
                  if (dot != -1) {
                    name = name.substring(0, dot) + StringUtil.toLowerCase(name.substring(dot));
                    File destParent = dest.getParentFile();
                    dest = destParent != null ? new File(destParent, name) : new File(name);
                    if (updateEncoding && isTextFile(source)) {
                      myImporter.copyTextFile(sourceModule, source, dest);
                    } else {
                      Files.copy(source, dest);
                    }
                    if (sourceModule != null) {
                      // Just use the names rather than the full paths to make it clear that this was just
                      // a file renaming (even though there is also a move happening for all resources including
                      // these. In other words, instead of displaying
                      // * res/drawable-hdpi/other_icon.PNG => app/src/main/res/drawable-hdpi/other_icon.png
                      // we display
                      // * other_icon.PNG => app/src/main/res/drawable-hdpi/other_icon.png
                      myImporter.getSummary().reportMoved(sourceModule, new File(source.getName()), new File(name));
                    }
                    return true;
                  }
                }
              }
            }
            return false;
          }
        }, true, this);
        summary.reportMoved(this, srcRes, destRes);
        recordCopiedFile(copied, srcRes);
      }
      File srcAssets = getAssetsDir();
      if (srcAssets != null && srcAssets.exists()) {
        File destAssets = new File(main, FD_ASSETS);
        myImporter.mkdirs(destAssets);
        myImporter.copyDir(srcAssets, destAssets, null, false, null);
        summary.reportMoved(this, srcAssets, destAssets);
        recordCopiedFile(copied, srcAssets);
      }

      File lintXml = getLintXml();
      if (lintXml != null) {
        File destLintXml = new File(destDir, lintXml.getName());
        myImporter.copyTextFile(this, lintXml, destLintXml);
        summary.reportMoved(this, lintXml, destLintXml);
        recordCopiedFile(copied, lintXml);
      }
    }

    for (final File src : getSourcePaths()) {
      final File srcJava = resolveFile(src);
      File destJava = new File(main, FD_JAVA);

      if (srcJava.isDirectory()) {
        // Merge all the separate source folders into a single one; they aren't allowed
        // to contain source file conflicts anyway
        myImporter.mkdirs(destJava);
      }
      else {
        destJava = new File(main, srcJava.getName());
      }

      myImporter.copyDir(srcJava, destJava, new GradleImport.CopyHandler() {
        // Handle moving .rs/.rsh/.fs files to main/rs/ and .aidl files to the
        // corresponding aidl package under main/aidl
        @Override
        public boolean handle(@NonNull File source, @NonNull File dest, boolean updateEncoding, @Nullable ImportModule sourceModule)
            throws IOException {
          String sourcePath = source.getPath();
          if (sourcePath.endsWith(DOT_AIDL)) {
            File aidlDir = new File(main, FD_AIDL);
            File relative = GradleImport.computeRelativePath(srcJava, source);
            if (relative == null) {
              relative = GradleImport.computeRelativePath(srcJava.getCanonicalFile(), source);
            }
            if (relative != null) {
              File destAidl = new File(aidlDir, relative.getPath());
              myImporter.mkdirs(destAidl.getParentFile());
              myImporter.copyTextFile(ImportModule.this, source, destAidl);
              myImporter.getSummary().reportMoved(ImportModule.this, source, destAidl);
              return true;
            }
          }
          else if (sourcePath.endsWith(DOT_RS) ||
                   sourcePath.endsWith(DOT_RSH) ||
                   sourcePath.endsWith(DOT_FS)) {
            // Copy to flattened rs dir
            // TODO: Ensure the file names are unique!
            File destRs = new File(main, FD_RENDERSCRIPT + separator +
                                         source.getName());
            myImporter.mkdirs(destRs.getParentFile());
            myImporter.copyTextFile(ImportModule.this, source, destRs);
            myImporter.getSummary().reportMoved(ImportModule.this, source, destRs);
            return true;
          } else if (!sourcePath.endsWith(DOT_JAVA)
                     && !sourcePath.endsWith(DOT_CLASS) // in case Eclipse built .class into source dir
                     && !sourcePath.endsWith(DOT_JAR)
                     && !sourcePath.equals("package.html") // leave docs with their code
                     && !sourcePath.equals("overview.html")
                     && source.isFile()) {
            // Move resources over to the resource folder
            File resourceDir = new File(main, FD_JAVA_RES);
            File relative = GradleImport.computeRelativePath(srcJava, source);
            if (relative == null) {
              relative = GradleImport.computeRelativePath(srcJava.getCanonicalFile(), source);
            }
            if (relative != null) {
              File destResource = new File(resourceDir, relative.getPath());
              myImporter.mkdirs(destResource.getParentFile());
              Files.copy(source, destResource);
              myImporter.getSummary().reportMoved(ImportModule.this, source, destResource);
              return true;
            }
          }
          return false;
        }
      }, true, this);
      summary.reportMoved(this, srcJava, destJava);
      recordCopiedFile(copied, srcJava);
    }

    for (File jar : getJarPaths()) {
      File srcJar = resolveFile(jar);
      File destJar = new File(destDir, getJarOutputRelativePath(jar).getPath());
      if (destJar.getParentFile() != null) {
        myImporter.mkdirs(destJar.getParentFile());
      }
      Files.copy(srcJar, destJar);
      summary.reportMoved(this, srcJar, destJar);
      recordCopiedFile(copied, srcJar);
    }

    for (File lib : getNativeLibs()) {
      File srcLib = resolveFile(lib);
      String abi = lib.getParentFile().getName();
      File destLib =
        new File(destDir, FD_SOURCES + separator + FD_MAIN + separator + "jniLibs" + separator + abi + separator + lib.getName());
      if (destLib.getParentFile() != null) {
        myImporter.mkdirs(destLib.getParentFile());
      }
      Files.copy(srcLib, destLib);
      summary.reportMoved(this, srcLib, destLib);
      recordCopiedFile(copied, srcLib);
    }

    File jni = getNativeSources();
    if (jni != null) {
      File srcJni = resolveFile(jni);
      File destJni = new File(destDir, FD_SOURCES + separator + FD_MAIN + separator + "jni");
      myImporter.copyDir(srcJni, destJni, null, true, this);
      summary.reportMoved(this, srcJni, destJni);
      recordCopiedFile(copied, srcJni);
    }

    File instrumentation = getInstrumentationDir();
    if (instrumentation != null) {
      final File test = new File(destDir, FD_SOURCES + separator + FD_TEST);
      myImporter.mkdirs(test);

      // We should NOT copy the Android manifest file. Don't mark it as "ignored"
      // either since we'll pull everything we need out of it and put it into the
      // Gradle file.
      recordCopiedFile(copied, new File(instrumentation, ANDROID_MANIFEST_XML));

      File srcRes = new File(instrumentation, FD_RES);
      if (srcRes.isDirectory()) {
        File destRes = new File(test, FD_RES);
        myImporter.mkdirs(destRes);
        myImporter.copyDir(srcRes, destRes, null, true, this);
        summary.reportMoved(this, srcRes, destRes);
        recordCopiedFile(copied, srcRes);
      }

      File srcJava = new File(instrumentation, FD_SOURCES);
      if (srcJava.isDirectory()) {
        File destRes = new File(test, FD_JAVA);
        myImporter.mkdirs(destRes);
        myImporter.copyDir(srcJava, destRes, null, true, this);
        summary.reportMoved(this, srcJava, destRes);
        recordCopiedFile(copied, srcJava);
      }

      for (File jar : getTestJarPaths()) {
        File srcJar = resolveFile(jar);
        File destJar = new File(destDir, getTestJarOutputRelativePath(jar).getPath());
        if (destJar.exists()) {
          continue;
        }
        if (destJar.getParentFile() != null) {
          myImporter.mkdirs(destJar.getParentFile());
        }
        Files.copy(srcJar, destJar);
        summary.reportMoved(this, srcJar, destJar);
        recordCopiedFile(copied, srcJar);
      }
    }

    if (isAndroidProject()) {
      for (File srcProguard : getLocalProguardFiles()) {
        File destProguard = new File(destDir, srcProguard.getName());
        if (!destProguard.exists()) {
          myImporter.copyTextFile(this, srcProguard, destProguard);
          summary.reportMoved(this, srcProguard, destProguard);
          recordCopiedFile(copied, srcProguard);
        }
        else {
          myImporter.reportWarning(this, destProguard, "Local proguard config file name is not unique");
        }
      }
    }

    reportIgnored(copied);
  }

  private static boolean hasUpperCaseExtension(@NonNull File file) {
    String path = file.getPath();
    int index = path.lastIndexOf(separatorChar);
    // Can be -1 (if this is just a file name, with no path); the below still works since we start from the next char (0)
    index = path.indexOf('.', index + 1);
    if (index == -1) {
      return false; // no extension in the file name
    }
    index++;

    for (; index < path.length(); index++) {
      if (Character.isUpperCase(path.charAt(index))) {
        return true;
      }
    }

    return false;
  }

  private void reportIgnored(Set<File> copied) throws IOException {
    File canonicalDir = getCanonicalModuleDir();

    // Ignore output folder (if not under bin/ as usual)
    File outputDir = getOutputDir();
    if (outputDir != null) {
      copied.add(resolveFile(outputDir).getCanonicalFile());
    }

    // These files are either not useful (bin, gen) or already handled (project metadata files)
    copied.add(new File(canonicalDir, BIN_FOLDER));
    copied.add(new File(canonicalDir, GEN_FOLDER));
    copied.add(new File(canonicalDir, ECLIPSE_DOT_CLASSPATH));
    copied.add(new File(canonicalDir, ECLIPSE_DOT_PROJECT));
    copied.add(new File(canonicalDir, FN_PROJECT_PROPERTIES));
    copied.add(new File(canonicalDir, FN_PROJECT_PROPERTIES));
    copied.add(new File(canonicalDir, FN_LOCAL_PROPERTIES));
    copied.add(new File(canonicalDir, LIBS_FOLDER));
    copied.add(new File(canonicalDir, ".settings"));
    copied.add(new File(canonicalDir, ".cproject"));
    if (isNdkProject()) {
      // TODO: Also resolve NDK_OUT in the Makefile in case a custom location is used
      copied.add(new File(canonicalDir, "obj"));
    }

    reportIgnored(canonicalDir, copied, 0);
  }

  /**
   * Report ignored files. Returns true if the file (and all its children) were
   * ignored too.
   */
  private boolean reportIgnored(@NonNull File file, @NonNull Set<File> copied, int depth) throws IOException {
    if (depth > 0 && copied.contains(file)) {
      return true;
    }

    boolean ignore = true;
    boolean isDirectory = file.isDirectory();
    if (isDirectory) {
      // Don't recursively list contents of .git etc
      if (depth == 1) {
        if (GradleImport.isIgnoredFile(file)) {
          return false;
        }
      }
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          ignore &= reportIgnored(child, copied, depth + 1);
        }
      }
    }
    else {
      ignore = false;
    }

    if (depth > 0 && !ignore) {
      File relative = GradleImport.computeRelativePath(getCanonicalModuleDir(), file);
      if (relative == null) {
        relative = file;
      }
      String path = relative.getPath();
      if (isDirectory) {
        path += separator;
      }
      myImporter.getSummary().reportIgnored(getOriginalName(), path);
    }

    return ignore;
  }

  public List<File> getJarDependencies() {
    return myJarDependencies;
  }

  public List<GradleCoordinate> getDependencies() {
    return myDependencies;
  }

  public List<File> getTestJarDependencies() {
    return myTestJarDependencies;
  }

  public List<GradleCoordinate> getTestDependencies() {
    return myTestDependencies;
  }

  @Nullable
  public File computeProjectRelativePath(@NonNull File file) throws IOException {
    return GradleImport.computeRelativePath(getCanonicalModuleDir(), file);
  }

  protected abstract boolean dependsOn(@NonNull ImportModule other);

  protected abstract boolean dependsOnLibrary(@NonNull String pkg);

  /**
   * Strip out .jar file dependencies (on files in libs/) that correspond
   * to code pulled in from a library dependency
   */
  void removeJarDependencies() {
    // For each module, remove any .jar files in its path that
    // provided b
    ListIterator<File> iterator = getJarPaths().listIterator();
    while (iterator.hasNext()) {
      File jar = iterator.next();
      if (myImporter.isJarHandled(jar)) {
        iterator.remove();
      }
      else {
        String pkg = jar.getName();
        if (pkg.endsWith(DOT_JAR)) {
          pkg = pkg.substring(0, pkg.length() - DOT_JAR.length());
        }
        pkg = pkg.replace('-', '.');
        if (dependsOnLibrary(pkg)) {
          iterator.remove();
        }
      }
    }
  }

  // Sort by dependency order
  @Override
  public int compareTo(@NonNull ImportModule other) {
    if (dependsOn(other)) {
      return 1;
    }
    else if (other.dependsOn(this)) {
      return -1;
    }
    else {
      return getOriginalName().compareTo(other.getOriginalName());
    }
  }
}
