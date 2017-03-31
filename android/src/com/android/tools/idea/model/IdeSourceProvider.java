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
package com.android.tools.idea.model;

import com.android.builder.model.SourceProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;

/**
 * Creates a deep copy of {@link SourceProvider}.
 *
 * @see IdeAndroidProject
 */
public class IdeSourceProvider implements SourceProvider, Serializable {
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

  public IdeSourceProvider(@NotNull SourceProvider provider) {
    myName = provider.getName();
    myManifestFile = provider.getManifestFile();
    myJavaDirectories = new ArrayList<>(provider.getJavaDirectories());
    myResourcesDirectories = new ArrayList<>(provider.getResourcesDirectories());
    myAidlDirectories = new ArrayList<>(provider.getAidlDirectories());
    myRenderscriptDirectories = new ArrayList<>(provider.getRenderscriptDirectories());
    myCDirectories = new ArrayList<>(provider.getCDirectories());
    myCppDirectories = new ArrayList<>(provider.getCppDirectories());
    myResDirectories = new ArrayList<>(provider.getResDirectories());
    myAssetsDirectories = new ArrayList<>(provider.getAssetsDirectories());
    myJniLibsDirectories = new ArrayList<>(provider.getJniLibsDirectories());
    myShadersDirectories = new ArrayList<>(provider.getShadersDirectories());
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
}
