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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

public class SourceProviderStub extends BaseStub implements SourceProvider {
  @NotNull private final String myName;
  @NotNull private final File myManifestFile;
  @NotNull private final Collection<File> myJavaDirectories;
  @NotNull private final Collection<File> myResourcesDirectories;
  @NotNull private final Collection<File> myAidlDirectories;
  @NotNull private final Collection<File> myRenderscriptDirectories;
  @NotNull private final Collection<File> myCDirectories;
  @NotNull private final Collection<File> myCppDirectories;
  @NotNull private final Collection<File> myResDirectories;
  @NotNull private final Collection<File> myAssetsDirectories;
  @NotNull private final Collection<File> myJniLibsDirectories;
  @NotNull private final Collection<File> myShadersDirectories;

  public SourceProviderStub() {
    this("name", new File("manifest"), new File("java"), new File("resources"), new File("aidl"), new File("renderscript"), new File("c"),
         new File("cpp"), new File("res"), new File("assets"), new File("jniLibs"), new File("shaders"));
  }

  public SourceProviderStub(@NotNull String name,
                            @NotNull File manifestFile,
                            @NotNull File javaDirectory,
                            @NotNull File resourcesDirectory,
                            @NotNull File aidlDirectory,
                            @NotNull File renderscriptDirectory,
                            @NotNull File cDirectory,
                            @NotNull File cppDirectory,
                            @NotNull File resDirectory,
                            @NotNull File assetsDirectory,
                            @NotNull File jniLibsDirectory,
                            @NotNull File shadersDirectory) {
    myName = name;
    myManifestFile = manifestFile;
    myJavaDirectories = Lists.newArrayList(javaDirectory);
    myResourcesDirectories = Lists.newArrayList(resourcesDirectory);
    myAidlDirectories = Lists.newArrayList(aidlDirectory);
    myRenderscriptDirectories = Lists.newArrayList(renderscriptDirectory);
    myCDirectories = Lists.newArrayList(cDirectory);
    myCppDirectories = Lists.newArrayList(cppDirectory);
    myResDirectories = Lists.newArrayList(resDirectory);
    myAssetsDirectories = Lists.newArrayList(assetsDirectory);
    myJniLibsDirectories = Lists.newArrayList(jniLibsDirectory);
    myShadersDirectories = Lists.newArrayList(shadersDirectory);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public File getManifestFile() {
    return myManifestFile;
  }

  @Override
  @NotNull
  public Collection<File> getJavaDirectories() {
    return myJavaDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getResourcesDirectories() {
    return myResourcesDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getAidlDirectories() {
    return myAidlDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getRenderscriptDirectories() {
    return myRenderscriptDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getCDirectories() {
    return myCDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getCppDirectories() {
    return myCppDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getResDirectories() {
    return myResDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getAssetsDirectories() {
    return myAssetsDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getJniLibsDirectories() {
    return myJniLibsDirectories;
  }

  @Override
  @NotNull
  public Collection<File> getShadersDirectories() {
    return myShadersDirectories;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SourceProvider)) {
      return false;
    }
    SourceProvider stub = (SourceProvider)o;
    return Objects.equals(getName(), stub.getName()) &&
           Objects.equals(getManifestFile(), stub.getManifestFile()) &&
           Objects.equals(getJavaDirectories(), stub.getJavaDirectories()) &&
           Objects.equals(getResourcesDirectories(), stub.getResourcesDirectories()) &&
           Objects.equals(getAidlDirectories(), stub.getAidlDirectories()) &&
           Objects.equals(getRenderscriptDirectories(), stub.getRenderscriptDirectories()) &&
           Objects.equals(getCDirectories(), stub.getCDirectories()) &&
           Objects.equals(getCppDirectories(), stub.getCppDirectories()) &&
           Objects.equals(getResDirectories(), stub.getResDirectories()) &&
           Objects.equals(getAssetsDirectories(), stub.getAssetsDirectories()) &&
           Objects.equals(getJniLibsDirectories(), stub.getJniLibsDirectories()) &&
           Objects.equals(getShadersDirectories(), stub.getShadersDirectories());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getManifestFile(), getJavaDirectories(), getResourcesDirectories(), getAidlDirectories(),
                        getRenderscriptDirectories(), getCDirectories(), getCppDirectories(), getResDirectories(), getAssetsDirectories(),
                        getJniLibsDirectories(), getShadersDirectories());
  }

  @Override
  public String toString() {
    return "SourceProviderStub{" +
           "myName='" + myName + '\'' +
           ", myManifestFile=" + myManifestFile +
           ", myJavaDirectories=" + myJavaDirectories +
           ", myResourcesDirectories=" + myResourcesDirectories +
           ", myAidlDirectories=" + myAidlDirectories +
           ", myRenderscriptDirectories=" + myRenderscriptDirectories +
           ", myCDirectories=" + myCDirectories +
           ", myCppDirectories=" + myCppDirectories +
           ", myResDirectories=" + myResDirectories +
           ", myAssetsDirectories=" + myAssetsDirectories +
           ", myJniLibsDirectories=" + myJniLibsDirectories +
           ", myShadersDirectories=" + myShadersDirectories +
           "}";
  }
}
