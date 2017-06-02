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

import com.android.builder.model.JavaLibrary;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class JavaLibraryStub extends LibraryStub implements JavaLibrary {
  @NotNull private final File myJarFile;
  @NotNull private final List<JavaLibrary> myDependencies;

  public JavaLibraryStub() {
    this(new File("jarFile"), Lists.newArrayList());
  }

  public JavaLibraryStub(@NotNull File jarFile, @NotNull List<JavaLibrary> dependencies) {
    myJarFile = jarFile;
    myDependencies = dependencies;
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public List<? extends JavaLibrary> getDependencies() {
    return myDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof JavaLibrary)) {
      return false;
    }
    JavaLibrary library = (JavaLibrary)o;
    return isProvided() == library.isProvided() &&
           Objects.equals(getResolvedCoordinates(), library.getResolvedCoordinates()) &&
           Objects.equals(getProject(), library.getProject()) &&
           Objects.equals(getName(), library.getName()) &&
           Objects.equals(getJarFile(), library.getJarFile()) &&
           Objects.equals(getDependencies(), library.getDependencies());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getResolvedCoordinates(), getProject(), getName(), isProvided(), getJarFile(), getDependencies());
  }

  @Override
  public String toString() {
    return "JavaLibraryStub{" +
           "myJarFile=" + myJarFile +
           ", myDependencies=" + myDependencies +
           "} " + super.toString();
  }
}
