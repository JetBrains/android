/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.stubs.level2;

import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.BaseStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class AndroidLibraryStub extends BaseStub implements Library {
  private final int myHashCode;
  private final int myType;
  @NotNull private final String myArtifactAddress;
  @NotNull private final File myFolder;
  @NotNull private final String myManifest;
  @NotNull private final String myJarFile;
  @NotNull private final String myResFolder;
  @NotNull private final String myAssetsFolder;
  @NotNull private final Collection<String> myLocalJars;
  @NotNull private final String myJniFolder;
  @NotNull private final String myAidlFolder;
  @NotNull private final String myRenderscriptFolder;
  @NotNull private final String myProguardRules;
  @NotNull private final String myLintJar;
  @NotNull private final String myExternalAnnotations;
  @NotNull private final String myPublicResources;
  @NotNull private final String mySymbolFile;
  @NotNull private final File myArtifactFile;

  public AndroidLibraryStub() {
    this(LIBRARY_ANDROID, "artifact:address:1.0", new File("libraryFolder"), "manifest.xml", "file.jar", "res", "assets",
         Collections.emptyList(), "jni", "aidl", "renderscriptFolder", "proguardRules", "lint.jar", "externalAnnotations",
         "publicResources", "symbolFile", new File("artifactFile"));
  }

  public AndroidLibraryStub(int type,
                            @NotNull String artifactAddress,
                            @NotNull File folder,
                            @NotNull String manifest,
                            @NotNull String jarFile,
                            @NotNull String resFolder,
                            @NotNull String assetsFolder,
                            @NotNull Collection<String> localJars,
                            @NotNull String jniFolder,
                            @NotNull String aidlFolder,
                            @NotNull String renderscriptFolder,
                            @NotNull String proguardRules,
                            @NotNull String lintJar,
                            @NotNull String externalAnnotations,
                            @NotNull String publicResources,
                            @NotNull String symbolFile,
                            @NotNull File artifactFile) {
    myType = type;
    myArtifactAddress = artifactAddress;
    myFolder = folder;
    myManifest = manifest;
    myJarFile = jarFile;
    myResFolder = resFolder;
    myAssetsFolder = assetsFolder;
    myLocalJars = localJars;
    myJniFolder = jniFolder;
    myAidlFolder = aidlFolder;
    myRenderscriptFolder = renderscriptFolder;
    myProguardRules = proguardRules;
    myLintJar = lintJar;
    myExternalAnnotations = externalAnnotations;
    myPublicResources = publicResources;
    mySymbolFile = symbolFile;
    myArtifactFile = artifactFile;
    myHashCode = calculateHashCode();
  }

  @Override
  public int getType() {
    return myType;
  }

  @Override
  @NotNull
  public String getArtifactAddress() {
    return myArtifactAddress;
  }

  @Override
  @NotNull
  public File getArtifact() {
    return myArtifactFile;
  }

  @Override
  @NotNull
  public File getFolder() {
    return myFolder;
  }

  @Override
  @NotNull
  public String getManifest() {
    return myManifest;
  }

  @Override
  @NotNull
  public String getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public String getResFolder() {
    return myResFolder;
  }

  @Override
  @NotNull
  public String getAssetsFolder() {
    return myAssetsFolder;
  }

  @Override
  @NotNull
  public Collection<String> getLocalJars() {
    return myLocalJars;
  }

  @Override
  @NotNull
  public String getJniFolder() {
    return myJniFolder;
  }

  @Override
  @NotNull
  public String getAidlFolder() {
    return myAidlFolder;
  }

  @Override
  @NotNull
  public String getRenderscriptFolder() {
    return myRenderscriptFolder;
  }

  @Override
  @NotNull
  public String getProguardRules() {
    return myProguardRules;
  }

  @Override
  @NotNull
  public String getLintJar() {
    return myLintJar;
  }

  @Override
  @NotNull
  public String getExternalAnnotations() {
    return myExternalAnnotations;
  }

  @Override
  @NotNull
  public String getPublicResources() {
    return myPublicResources;
  }

  @Override
  @NotNull
  public String getSymbolFile() {
    return mySymbolFile;
  }

  @Override
  @Nullable
  public String getVariant() {
    throw new UnsupportedOperationException(
      "getVariant() cannot be called when getType() returns ANDROID_LIBRARY");
  }

  @Override
  @NotNull
  public String getProjectPath() {
    throw new UnsupportedOperationException(
      "getProjectPath() cannot be called when getType() returns ANDROID_LIBRARY");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Library)) {
      return false;
    }
    Library that = (Library)o;
    return myType == that.getType() &&
           Objects.equals(myArtifactAddress, that.getArtifactAddress()) &&
           Objects.equals(myFolder, that.getFolder()) &&
           Objects.equals(myManifest, that.getManifest()) &&
           Objects.equals(myJarFile, that.getJarFile()) &&
           Objects.equals(myResFolder, that.getResFolder()) &&
           Objects.equals(myAssetsFolder, that.getAssetsFolder()) &&
           Objects.equals(myLocalJars, that.getLocalJars()) &&
           Objects.equals(myJniFolder, that.getJniFolder()) &&
           Objects.equals(myAidlFolder, that.getAidlFolder()) &&
           Objects.equals(myRenderscriptFolder, that.getRenderscriptFolder()) &&
           Objects.equals(myProguardRules, that.getProguardRules()) &&
           Objects.equals(myLintJar, that.getLintJar()) &&
           Objects.equals(myExternalAnnotations, that.getExternalAnnotations()) &&
           Objects.equals(myPublicResources, that.getPublicResources()) &&
           Objects.equals(mySymbolFile, that.getSymbolFile()) &&
           Objects.equals(myArtifactFile, that.getArtifact());
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects
      .hash(myType, myArtifactAddress, myFolder, myManifest, myJarFile, myResFolder, myAssetsFolder, myLocalJars, myJniFolder, myAidlFolder,
            myRenderscriptFolder, myProguardRules, myLintJar, myExternalAnnotations, myPublicResources, mySymbolFile, myArtifactFile);
  }

  @Override
  public String toString() {
    return "Level2AndroidLibraryStub{" +
           "myType=" + myType +
           ", myArtifactAddress='" + myArtifactAddress + '\'' +
           ", myFolder=" + myFolder +
           ", myManifest='" + myManifest + '\'' +
           ", myJarFile='" + myJarFile + '\'' +
           ", myResFolder='" + myResFolder + '\'' +
           ", myAssetsFolder='" + myAssetsFolder + '\'' +
           ", myLocalJars=" + myLocalJars +
           ", myJniFolder='" + myJniFolder + '\'' +
           ", myAidlFolder='" + myAidlFolder + '\'' +
           ", myRenderscriptFolder='" + myRenderscriptFolder + '\'' +
           ", myProguardRules='" + myProguardRules + '\'' +
           ", myLintJar='" + myLintJar + '\'' +
           ", myExternalAnnotations='" + myExternalAnnotations + '\'' +
           ", myPublicResources='" + myPublicResources + '\'' +
           ", mySymbolFile='" + mySymbolFile + '\'' +
           ", myArtifactFile=" + myArtifactFile +
           '}';
  }
}
