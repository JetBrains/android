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

import com.android.builder.model.AndroidAtom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Creates a deep copy of an {@link AndroidAtom}.
 */
public final class IdeAndroidAtom extends IdeAndroidBundle implements AndroidAtom {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myAtomName;
  @NotNull private final List<? extends AndroidAtom> myAtomDependencies;
  @NotNull private final File myDexFolder;
  @NotNull private final File myLibFolder;
  @NotNull private final File myJavaResFolder;
  @NotNull private final File myResourcePackage;
  private final int myHashCode;

  public IdeAndroidAtom(@NotNull AndroidAtom atom, @NotNull ModelCache modelCache) {
    super(atom, modelCache);
    myAtomName = atom.getAtomName();
    //
    List<? extends AndroidAtom> atomDependencies = atom.getAtomDependencies();
    myAtomDependencies = copy(atomDependencies, modelCache, dependency -> new IdeAndroidAtom(dependency, modelCache));

    myDexFolder = atom.getDexFolder();
    myLibFolder = atom.getLibFolder();
    myJavaResFolder = atom.getJavaResFolder();
    myResourcePackage = atom.getResourcePackage();

    myHashCode = calculateHashCode();
  }

  @Override
  @NotNull
  public String getAtomName() {
    return myAtomName;
  }

  @Override
  @NotNull
  public List<? extends AndroidAtom> getAtomDependencies() {
    return myAtomDependencies;
  }

  @Override
  @NotNull
  public File getDexFolder() {
    return myDexFolder;
  }

  @Override
  @NotNull
  public File getLibFolder() {
    return myLibFolder;
  }

  @Override
  @NotNull
  public File getJavaResFolder() {
    return myJavaResFolder;
  }

  @Override
  @NotNull
  public File getResourcePackage() {
    return myResourcePackage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeAndroidAtom)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeAndroidAtom atom = (IdeAndroidAtom)o;
    return atom.canEqual(this) &&
           Objects.equals(myAtomName, atom.myAtomName) &&
           Objects.equals(myAtomDependencies, atom.myAtomDependencies) &&
           Objects.equals(myDexFolder, atom.myDexFolder) &&
           Objects.equals(myLibFolder, atom.myLibFolder) &&
           Objects.equals(myJavaResFolder, atom.myJavaResFolder) &&
           Objects.equals(myResourcePackage, atom.myResourcePackage);
  }

  @Override
  public boolean canEqual(Object other) {
    return other instanceof IdeAndroidAtom;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  protected int calculateHashCode() {
    return Objects.hash(super.hashCode(), myAtomName, myAtomDependencies, myDexFolder, myLibFolder, myJavaResFolder,
                        myResourcePackage);
  }

  @Override
  public String toString() {
    return "IdeAndroidAtom{" +
           super.toString() +
           ", myAtomName='" + myAtomName + '\'' +
           ", myAtomDependencies=" + myAtomDependencies +
           ", myDexFolder=" + myDexFolder +
           ", myLibFolder=" + myLibFolder +
           ", myJavaResFolder=" + myJavaResFolder +
           ", myResourcePackage=" + myResourcePackage +
           "}";
  }
}
