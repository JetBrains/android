/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.gradle;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings;
import org.gradle.tooling.model.idea.IdeaLanguageLevel;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class IdeaProjectStub implements IdeaProject {
  @NotNull private final List<IdeaModuleStub> modules = Lists.newArrayList();

  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final File myBuildFile;

  public IdeaProjectStub(@NotNull String name) {
    myName = name;
    myFileStructure = new FileStructure(name);
    myBuildFile = myFileStructure.createProjectFile(SdkConstants.FN_BUILD_GRADLE);
  }

  @Override
  public IdeaJavaLanguageSettings getJavaLanguageSettings() throws UnsupportedMethodException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getJdkName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public IdeaLanguageLevel getLanguageLevel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public HierarchicalElement getParent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public DomainObjectSet<? extends IdeaModule> getChildren() {
    return getModules();
  }

  @Override
  public DomainObjectSet<? extends IdeaModule> getModules() {
    return ImmutableDomainObjectSet.of(modules);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public String getDescription() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public IdeaModuleStub addModule(@NotNull String name, @NotNull String...tasks) {
    IdeaModuleStub module = new IdeaModuleStub(name, this, tasks);
    modules.add(module);
    return module;
  }

  /**
   * Deletes this project's directory structure.
   */
  public void dispose() {
    myFileStructure.dispose();
  }

  /**
   * @return this project's root directory.
   */
  @NotNull
  public File getRootDir() {
    return myFileStructure.getRootDir();
  }

  /**
   * @return this project's build.gradle file.
   */
  @NotNull
  public File getBuildFile() {
    return myBuildFile;
  }
}
