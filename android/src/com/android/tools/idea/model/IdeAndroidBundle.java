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

import com.android.annotations.Nullable;
import com.android.builder.model.AndroidBundle;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Creates a deep copy of {@link AndroidBundle}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidBundle extends IdeLibrary implements AndroidBundle, Serializable {
  @NotNull private final File myBundle;
  @NotNull private final File myFolder;
  @NotNull private final List<IdeAndroidLibrary> myLibraryDependencies;
  @NotNull private final Collection<IdeJavaLibrary> myJavaDependencies;
  @NotNull private final File myManifest;
  @NotNull private final File myJarFile;
  @NotNull private final File myResFolder;
  @NotNull private final File myAssetsFolder;
  @Nullable private final String myProjectVariant;

  public IdeAndroidBundle(@NotNull AndroidBundle bundle, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    super(bundle, gradleVersion);
    myBundle = bundle.getBundle();
    myFolder = bundle.getFolder();

    myLibraryDependencies = new ArrayList<>();
    for (AndroidLibrary dependency : bundle.getLibraryDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeAndroidLibrary(dependency, seen, gradleVersion));
      }
      myLibraryDependencies.add((IdeAndroidLibrary)seen.get(dependency));
    }

    myJavaDependencies = new ArrayList<>();
    for (JavaLibrary dependency : bundle.getJavaDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeJavaLibrary(dependency, seen, gradleVersion));
      }
      myJavaDependencies.add((IdeJavaLibrary)seen.get(dependency));
    }

    myManifest = bundle.getManifest();
    myJarFile = bundle.getJarFile();
    myResFolder = bundle.getResFolder();
    myAssetsFolder = bundle.getAssetsFolder();
    myProjectVariant = bundle.getProjectVariant();
  }

  @Override
  @NotNull
  public File getBundle() {
    return myBundle;
  }

  @Override
  @NotNull
  public File getFolder() {
    return myFolder;
  }

  @Override
  @NotNull
  public List<? extends AndroidLibrary> getLibraryDependencies() {
    return myLibraryDependencies;
  }

  @Override
  @NotNull
  public Collection<? extends JavaLibrary> getJavaDependencies() {
    return myJavaDependencies;
  }

  @Override
  @NotNull
  public File getManifest() {
    return myManifest;
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public File getResFolder() {
    return myResFolder;
  }

  @Override
  @NotNull
  public File getAssetsFolder() {
    return myAssetsFolder;
  }

  @Nullable
  @Override
  public String getProjectVariant() {
    return myProjectVariant;
  }
}
