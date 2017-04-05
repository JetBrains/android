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

import com.android.builder.model.AndroidAtom;
import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates a deep copy of {@link AndroidAtom}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidAtom extends IdeAndroidBundle implements AndroidAtom, Serializable {
  @NotNull private final String myAtomName;
  @NotNull private final List<IdeAndroidAtom> myAtomDependencies;
  @NotNull private final File myDexFolder;
  @NotNull private final File myLibFolder;
  @NotNull private final File myJavaResFolder;
  @NotNull private final File myResourcePackage;

  public IdeAndroidAtom(@NotNull AndroidAtom atom, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    super(atom, seen, gradleVersion);

    myAtomName = atom.getAtomName();

    myAtomDependencies = new ArrayList<>();
    for (AndroidAtom dependency : atom.getAtomDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeAndroidAtom(dependency, seen, gradleVersion));
      }
      myAtomDependencies.add((IdeAndroidAtom)seen.get(dependency));
    }

    myDexFolder = atom.getDexFolder();
    myLibFolder = atom.getLibFolder();
    myJavaResFolder = atom.getJavaResFolder();
    myResourcePackage = atom.getResourcePackage();
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
}
