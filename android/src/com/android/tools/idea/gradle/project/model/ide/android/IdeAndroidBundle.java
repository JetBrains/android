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

import com.android.builder.model.AndroidBundle;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Creates a deep copy of an {@link AndroidBundle}.
 */
public abstract class IdeAndroidBundle extends IdeLibrary implements AndroidBundle {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private final File myBundle;
  @NotNull private final File myFolder;
  @NotNull private final List<? extends AndroidLibrary> myLibraryDependencies;
  @NotNull private final Collection<? extends JavaLibrary> myJavaDependencies;
  @NotNull private final File myManifest;
  @NotNull private final File myJarFile;
  @NotNull private final File myResFolder;
  @NotNull private final File myAssetsFolder;
  @Nullable private final String myProjectVariant;
  private final int myHashCode;

  protected IdeAndroidBundle(@NotNull AndroidBundle bundle, @NotNull ModelCache modelCache) {
    super(bundle, modelCache);
    myBundle = bundle.getBundle();
    myFolder = bundle.getFolder();

    myLibraryDependencies = copy(bundle.getLibraryDependencies(), modelCache, library -> new IdeAndroidLibrary(library, modelCache));
    myJavaDependencies = copyJavaDependencies(bundle, modelCache);

    myManifest = bundle.getManifest();
    myJarFile = bundle.getJarFile();
    myResFolder = bundle.getResFolder();
    myAssetsFolder = bundle.getAssetsFolder();
    myProjectVariant = bundle.getProjectVariant();

    myHashCode = calculateHashCode();
  }


  @NotNull
  private static Collection<? extends JavaLibrary> copyJavaDependencies(@NotNull AndroidBundle bundle, @NotNull ModelCache modelCache) {
    Collection<? extends JavaLibrary> javaDependencies;
    try {
      javaDependencies = bundle.getJavaDependencies();
    }
    catch (UnsupportedMethodException ignored) {
      return Collections.emptyList();
    }
    return copy(javaDependencies, modelCache, library -> new IdeJavaLibrary(library, modelCache));
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

  @Override
  @Nullable
  public String getProjectVariant() {
    return myProjectVariant;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeAndroidBundle)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeAndroidBundle bundle = (IdeAndroidBundle)o;
    return bundle.canEqual(this) &&
           Objects.equals(myBundle, bundle.myBundle) &&
           Objects.equals(myFolder, bundle.myFolder) &&
           Objects.equals(myLibraryDependencies, bundle.myLibraryDependencies) &&
           Objects.equals(myJavaDependencies, bundle.myJavaDependencies) &&
           Objects.equals(myManifest, bundle.myManifest) &&
           Objects.equals(myJarFile, bundle.myJarFile) &&
           Objects.equals(myResFolder, bundle.myResFolder) &&
           Objects.equals(myAssetsFolder, bundle.myAssetsFolder) &&
           Objects.equals(myProjectVariant, bundle.myProjectVariant);
  }

  @Override
  public boolean canEqual(Object other) {
    return other instanceof IdeAndroidBundle;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  protected int calculateHashCode() {
    return Objects.hash(super.calculateHashCode(), myBundle, myFolder, myLibraryDependencies, myJavaDependencies, myManifest, myJarFile,
                        myResFolder, myAssetsFolder, myProjectVariant);
  }

  @Override
  public String toString() {
    return super.toString() +
           "myBundle=" + myBundle +
           ", myFolder=" + myFolder +
           ", myLibraryDependencies=" + myLibraryDependencies +
           ", myJavaDependencies=" + myJavaDependencies +
           ", myManifest=" + myManifest +
           ", myJarFile=" + myJarFile +
           ", myResFolder=" + myResFolder +
           ", myAssetsFolder=" + myAssetsFolder +
           ", myProjectVariant='" + myProjectVariant + '\'';
  }
}
