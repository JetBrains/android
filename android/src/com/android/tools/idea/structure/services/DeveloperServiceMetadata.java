/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.services;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * A developer service is an association of code dependencies, manifest files, code samples, and
 * other data values, all of which can be used to simplify adding an external feature into the
 * user's code base. This class maintains the data that defines a developer service at a high
 * level, including name, description, and various options.
 */
public final class DeveloperServiceMetadata {
  /**
   * A unique string identifying a given feature/service. For features that are part of a collection or relatively common names, namespaces
   * are suggested. For example "firebase.app_invites". This is used by plugins to retrieve the appropriate DeveloperService instance by
   * id when necessary.
   */
  @NotNull private final String myId;
  @NotNull private final String myName;
  @NotNull private final String myDescription;
  @NotNull private final Icon myIcon;

  @NotNull private final List<String> myDependencies = Lists.newArrayList();
  @NotNull private final List<String> myPermissions = Lists.newArrayList();
  @NotNull private final List<String> myModifiedFiles = Lists.newArrayList();

  @Nullable private URI myLearnMoreLink;
  @Nullable private URI myApiLink;

  public DeveloperServiceMetadata(@NotNull String id, @NotNull String name, @NotNull String description, @NotNull Icon icon) {
    myId = id;
    myName = name;
    myDescription = description;
    myIcon = icon;
  }

  public void addDependency(@NotNull String dependency) {
    myDependencies.add(dependency);
  }

  public void addPermission(@NotNull String permission) {
    myPermissions.add(permission);
  }

  public void addModifiedFile(@NotNull File file) {
    myModifiedFiles.add(file.getName());
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public URI getLearnMoreLink() {
    return myLearnMoreLink;
  }

  public void setLearnMoreLink(@NotNull URI learnMoreLink) {
    myLearnMoreLink = learnMoreLink;
  }

  @Nullable
  public URI getApiLink() {
    return myApiLink;
  }

  public void setApiLink(@NotNull URI apiLink) {
    myApiLink = apiLink;
  }

  @NotNull
  public List<String> getDependencies() {
    return myDependencies;
  }

  @NotNull
  public List<String> getModifiedFiles() {
    return myModifiedFiles;
  }

  @NotNull
  public List<String> getPermissions() {
    return myPermissions;
  }
}
