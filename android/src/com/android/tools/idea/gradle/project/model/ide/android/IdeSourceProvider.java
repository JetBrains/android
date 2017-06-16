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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link SourceProvider}.
 */
public final class IdeSourceProvider extends IdeModel implements SourceProvider {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

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
  private final int myHashCode;

  public IdeSourceProvider(@NotNull SourceProvider provider, @NotNull ModelCache modelCache) {
    super(provider, modelCache);
    myName = provider.getName();
    myManifestFile = provider.getManifestFile();
    myJavaDirectories = ImmutableList.copyOf(provider.getJavaDirectories());
    myResourcesDirectories = ImmutableList.copyOf(provider.getResourcesDirectories());
    myAidlDirectories = ImmutableList.copyOf(provider.getAidlDirectories());
    myRenderscriptDirectories = ImmutableList.copyOf(provider.getRenderscriptDirectories());
    myCDirectories = ImmutableList.copyOf(provider.getCDirectories());
    myCppDirectories = ImmutableList.copyOf(provider.getCppDirectories());
    myResDirectories = ImmutableList.copyOf(provider.getResDirectories());
    myAssetsDirectories = ImmutableList.copyOf(provider.getAssetsDirectories());
    myJniLibsDirectories = ImmutableList.copyOf(provider.getJniLibsDirectories());
    myShadersDirectories = copyNewProperty(() -> ImmutableList.copyOf(provider.getShadersDirectories()), Collections.emptyList());
    myHashCode = calculateHashCode();
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
    if (!(o instanceof IdeSourceProvider)) {
      return false;
    }
    IdeSourceProvider provider = (IdeSourceProvider)o;
    return Objects.equals(myName, provider.myName) &&
           Objects.equals(myManifestFile, provider.myManifestFile) &&
           Objects.equals(myJavaDirectories, provider.myJavaDirectories) &&
           Objects.equals(myResourcesDirectories, provider.myResourcesDirectories) &&
           Objects.equals(myAidlDirectories, provider.myAidlDirectories) &&
           Objects.equals(myRenderscriptDirectories, provider.myRenderscriptDirectories) &&
           Objects.equals(myCDirectories, provider.myCDirectories) &&
           Objects.equals(myCppDirectories, provider.myCppDirectories) &&
           Objects.equals(myResDirectories, provider.myResDirectories) &&
           Objects.equals(myAssetsDirectories, provider.myAssetsDirectories) &&
           Objects.equals(myJniLibsDirectories, provider.myJniLibsDirectories) &&
           Objects.equals(myShadersDirectories, provider.myShadersDirectories);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myName, myManifestFile, myJavaDirectories, myResourcesDirectories, myAidlDirectories,
                        myRenderscriptDirectories, myCDirectories, myCppDirectories, myResDirectories, myAssetsDirectories,
                        myJniLibsDirectories, myShadersDirectories);
  }

  @Override
  public String toString() {
    return "IdeSourceProvider{" +
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
           '}';
  }
}
