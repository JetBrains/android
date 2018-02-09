/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant;

import com.android.tools.idea.templates.recipe.Recipe;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Container for instruction details from a {@code Recipe} instance.
 */
public class RecipeMetadata {

  @NotNull private final List<String> myDependencies = Lists.newArrayList();
  @NotNull private final List<String> myClasspathEntries = Lists.newArrayList();
  @NotNull private final List<String> myPlugins = Lists.newArrayList();
  @NotNull private final List<String> myPermissions = Lists.newArrayList();
  @NotNull private final List<String> myModifiedFiles = Lists.newArrayList();
  @NotNull private final Module myModule;
  @NotNull private final Recipe myRecipe;

  public RecipeMetadata(@NotNull Recipe recipe, @NotNull Module module) {
    myRecipe = recipe;
    myModule = module;
  }

  public void addDependency(@NotNull String dependency) {
    myDependencies.add(dependency);
  }

  public void addClasspathEntry(@NotNull String classpathEntry) {
    myClasspathEntries.add(classpathEntry);
  }

  public void addPlugin(@NotNull String plugin) {
    myPlugins.add(plugin);
  }

  public void addPermission(@NotNull String permission) {
    myPermissions.add(permission);
  }

  public void addModifiedFile(@NotNull File file) {
    myModifiedFiles.add(file.getName());
  }

  @NotNull
  public List<String> getDependencies() {
    return myDependencies;
  }

  @NotNull
  public List<String> getClasspathEntries() {
    return myClasspathEntries;
  }

  @NotNull
  public List<String> getPlugins() {
    return myPlugins;
  }

  @NotNull
  public List<String> getModifiedFiles() {
    return myModifiedFiles;
  }

  @NotNull
  public List<String> getPermissions() {
    return myPermissions;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public Recipe getRecipe() {
    return myRecipe;
  }
}
