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

import static com.android.tools.idea.gradle.util.ImportUtil.APPCOMPAT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.GRIDLAYOUT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.MEDIA_ROUTER_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.SUPPORT_ARTIFACT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.AndroidVersion;
import com.android.tools.lint.client.api.LintXmlConfiguration;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An imported module from Eclipse
 */
class EclipseImportModule extends ImportModule {
  private final EclipseProject myProject;
  private List<ImportModule> myDirectDependencies;
  private List<ImportModule> myAllDependencies;

  public EclipseImportModule(@NonNull GradleImport importer, @NonNull EclipseProject project) {
    super(importer);
    myProject = project;
    myProject.setModule(this);
  }

  @Nullable
  @Override
  protected File getLintXml() {
    File lintXml = new File(myProject.getDir(), LintXmlConfiguration.CONFIG_FILE_NAME);
    return lintXml.exists() ? lintXml : null;
  }

  @Override
  @NonNull
  protected File resolveFile(@NonNull File file) {
    if (file.isAbsolute()) {
      return file;
    }
    else {
      return new File(myProject.getDir(), file.getPath());
    }
  }

  @Override
  protected void initDependencies() {
    super.initDependencies();

    for (String reference : myProject.getInferredLibraries()) {
      if (reference.equals(APPCOMPAT_ARTIFACT)) {
        GradleCoordinate dependency = getAppCompatDependency();
        if (dependency != null) {
          myDependencies.add(dependency);
          myImporter.getSummary().reportReplacedLib(reference, Collections.singletonList(dependency));
        }
      } else if (reference.equals(SUPPORT_ARTIFACT)) {
        GradleCoordinate dependency = getSupportLibDependency();
        if (dependency != null) {
          myDependencies.add(dependency);
          myImporter.getSummary().reportReplacedLib(reference, Collections.singletonList(dependency));
        }
      } else if (reference.equals(GRIDLAYOUT_ARTIFACT)) {
        GradleCoordinate dependency = getGridLayoutDependency();
        if (dependency != null) {
          myDependencies.add(dependency);
          myImporter.getSummary().reportReplacedLib(reference, Collections.singletonList(dependency));
        }
      } else if (reference.equals(MEDIA_ROUTER_ARTIFACT)) {
        GradleCoordinate dependency = getMediaRouterDependency();
        if (dependency != null) {
          myDependencies.add(dependency);
          myImporter.getSummary().reportReplacedLib(reference, Collections.singletonList(dependency));
        }
      }
    }

    for (File jar : myProject.getJarPaths()) {
      if (myImporter.isReplaceJars()) {
        GradleCoordinate dependency = guessDependency(jar);
        if (dependency != null) {
          myDependencies.add(dependency);
          myImporter.getSummary().reportReplacedJar(jar, dependency);
          continue;
        }
      }
      myJarDependencies.add(getJarOutputRelativePath(jar));
    }

    for (File jar : myProject.getTestJarPaths()) {
      if (myImporter.isReplaceJars()) {
        GradleCoordinate dependency = guessDependency(jar);
        if (dependency != null) {
          myTestDependencies.add(dependency);
          myImporter.getSummary().reportReplacedJar(jar, dependency);
          continue;
        }
      }
      // Test jars unconditionally get copied into the libs/ folder
      myTestJarDependencies.add(getTestJarOutputRelativePath(jar));
    }
  }

  public void addDependencies(@NonNull List<GradleCoordinate> dependencies) {
    for (GradleCoordinate dependency : dependencies) {
      if (!myDependencies.contains(dependency)) {
        myDependencies.addAll(dependencies);
      }
    }
  }

  @Override
  protected boolean dependsOnLibrary(@NonNull String pkg) {
    if (!isAndroidProject()) {
      return false;
    }
    if (pkg.equals(myProject.getPackage())) {
      return true;
    }

    for (EclipseProject project : myProject.getAllLibraries()) {
      if (project.isAndroidProject() && pkg.equals(project.getPackage())) {
        return true;
      }
    }

    return false;
  }

  @NonNull
  @Override
  protected List<ImportModule> getDirectDependencies() {
    if (myDirectDependencies == null) {
      myDirectDependencies = new ArrayList<>();
      for (EclipseProject project : myProject.getDirectLibraries()) {
        EclipseImportModule module = project.getModule();
        if (module != null) {
          myDirectDependencies.add(module);
        }
      }
    }

    return myDirectDependencies;
  }

  @NonNull
  @Override
  protected List<ImportModule> getAllDependencies() {
    if (myAllDependencies == null) {
      myAllDependencies = new ArrayList<>();
      for (EclipseProject project : myProject.getAllLibraries()) {
        EclipseImportModule module = project.getModule();
        if (module != null) {
          myAllDependencies.add(module);
        }
      }
    }

    return myAllDependencies;
  }

  @Override
  protected Charset getProjectEncoding(@NonNull File file) {
    return myProject.getProjectEncoding();
  }

  @Override
  protected Charset getFileEncoding(@NonNull File file) {
    return myProject.getFileEncoding(file);
  }

  @NonNull
  @Override
  public File getDir() {
    return myProject.getDir();
  }

  @Override
  protected boolean isAndroidProject() {
    return myProject.isAndroidProject();
  }

  @Override
  protected boolean isLibrary() {
    return myProject.isLibrary();
  }

  @Nullable
  @Override
  protected String getPackage() {
    return myProject.getPackage();
  }

  @NonNull
  @Override
  protected String getOriginalName() {
    return myProject.getName();
  }

  @Override
  public boolean isApp() {
    return myProject.isAndroidProject() && !myProject.isLibrary();
  }

  @Override
  public boolean isAndroidLibrary() {
    return myProject.isAndroidProject() && myProject.isLibrary();
  }

  @Override
  public boolean isJavaLibrary() {
    return !myProject.isAndroidProject();
  }

  @Override
  public boolean isNdkProject() {
    return myProject.isNdkProject();
  }

  @Override
  @Nullable
  protected File getManifestFile() {
    return myProject.getManifestFile();
  }

  @Override
  @Nullable
  protected File getResourceDir() {
    return myProject.getResourceDir();
  }

  @Override
  @Nullable
  protected File getAssetsDir() {
    return myProject.getAssetsDir();
  }

  @Override
  @NonNull
  protected List<File> getSourcePaths() {
    return myProject.getSourcePaths();
  }

  @Override
  @NonNull
  protected List<File> getJarPaths() {
    return myProject.getJarPaths();
  }

  @Override
  @NonNull
  protected List<File> getTestJarPaths() {
    return myProject.getTestJarPaths();
  }

  @Override
  @NonNull
  protected List<File> getNativeLibs() {
    return myProject.getNativeLibs();
  }

  @Override
  @Nullable
  protected File getNativeSources() {
    return myProject.getNativeSources();
  }

  @Nullable
  @Override
  protected String getNativeModuleName() {
    return myProject.getNativeModuleName();
  }

  @Override
  @NonNull
  protected List<File> getLocalProguardFiles() {
    return myProject.getLocalProguardFiles();
  }

  @NonNull
  @Override
  protected List<File> getSdkProguardFiles() {
    return myProject.getSdkProguardFiles();
  }

  @NonNull
  @Override
  protected File getCanonicalModuleDir() {
    return myProject.getCanonicalDir();
  }

  @Nullable
  @Override
  protected File getOutputDir() {
    return myProject.getOutputDir();
  }

  @NonNull
  @Override
  protected String getLanguageLevel() {
    return myProject.getLanguageLevel();
  }

  @NonNull
  @Override
  protected AndroidVersion getCompileSdkVersion() {
    return myProject.getCompileSdkVersion();
  }

  @Nullable
  @Override
  protected String getAddOn() {
    return myProject.getAddOn();
  }

  @NonNull
  @Override
  protected AndroidVersion getTargetSdkVersion() {
    return myProject.getTargetSdkVersion();
  }

  @NonNull
  @Override
  protected AndroidVersion getMinSdkVersion() {
    return myProject.getMinSdkVersion();
  }

  @Override
  protected boolean dependsOn(@NonNull ImportModule other) {
    return myProject.getAllLibraries().contains(((EclipseImportModule)other).myProject);
  }

  @NonNull
  public EclipseProject getProject() {
    return myProject;
  }

  @Nullable
  @Override
  protected File getInstrumentationDir() {
    return myProject.getInstrumentationDir();
  }
}
