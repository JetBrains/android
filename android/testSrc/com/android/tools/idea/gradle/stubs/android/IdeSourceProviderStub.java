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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.FileStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Creates a version of {@link SourceProviderStub} that does not cause unsupported exceptions, used for testing {@link IdeAndroidProject}.
 *
 */
public class IdeSourceProviderStub extends SourceProviderStub {

  private String myName = "";

  public IdeSourceProviderStub(@NotNull FileStructure fileStructure, @NotNull String name) {
    super(fileStructure);
    myName = name;
    myManifestFile = null;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public File getManifestFile() {
    return myManifestFile;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SourceProvider)) return false;

    SourceProvider stub = (SourceProvider)o;

    if (getName() != null ? !getName().equals(stub.getName()) : stub.getName() != null) return false;

    //Since collections can be of different type (ArraysList vs HashSet for example) need to check that they contain the same elements
    if (!this.getAidlDirectories().containsAll(stub.getAidlDirectories())) return false;
    if (!stub.getAidlDirectories().containsAll(this.getAidlDirectories())) return false;

    if (!this.getAssetsDirectories().containsAll(stub.getAssetsDirectories())) return false;
    if (!stub.getAssetsDirectories().containsAll(this.getAssetsDirectories())) return false;

    if (!this.getCDirectories().containsAll(stub.getCDirectories())) return false;
    if (!stub.getCDirectories().containsAll(this.getCDirectories())) return false;

    if (!this.getCppDirectories().containsAll(stub.getCppDirectories())) return false;
    if (!stub.getCppDirectories().containsAll(this.getCppDirectories())) return false;

    if (!this.getJavaDirectories().containsAll(stub.getAidlDirectories())) return false;
    if (!stub.getJavaDirectories().containsAll(this.getAidlDirectories())) return false;

    if (!this.getJniLibsDirectories().containsAll(stub.getJniLibsDirectories())) return false;
    if (!stub.getJniLibsDirectories().containsAll(this.getJniLibsDirectories())) return false;

    if (!this.getRenderscriptDirectories().containsAll(stub.getRenderscriptDirectories())) return false;
    if (!stub.getRenderscriptDirectories().containsAll(this.getRenderscriptDirectories())) return false;

    if (!this.getResDirectories().containsAll(stub.getResDirectories())) return false;
    if (!stub.getResDirectories().containsAll(this.getResDirectories())) return false;

    if (!this.getResourcesDirectories().containsAll(stub.getResourcesDirectories())) return false;
    if (!stub.getResourcesDirectories().containsAll(this.getResourcesDirectories())) return false;

    if (!this.getShadersDirectories().containsAll(stub.getShadersDirectories())) return false;
    if (!stub.getShadersDirectories().containsAll(this.getShadersDirectories())) return false;

    return true;
  }

}
