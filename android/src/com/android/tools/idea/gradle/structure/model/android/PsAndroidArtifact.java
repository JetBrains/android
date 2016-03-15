/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.BaseArtifact;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.builder.model.AndroidProject.*;
import static com.intellij.icons.AllIcons.Modules.TestRoot;
import static com.intellij.icons.AllIcons.Nodes.Artifact;
import static icons.AndroidIcons.AndroidTestRoot;

public class PsAndroidArtifact extends PsChildModel implements PsAndroidModel {
  @NotNull private final String myName;
  @NotNull private final String myResolvedName;
  @NotNull private final Icon myIcon;

  @Nullable private final BaseArtifact myResolvedModel;

  PsAndroidArtifact(@NotNull PsVariant parent, @NotNull String resolvedName, @Nullable BaseArtifact resolvedModel) {
    super(parent);
    myResolvedName = resolvedName;
    Icon icon = Artifact;
    String name = "";
    if (ARTIFACT_MAIN.equals(resolvedName)) {
      icon = AllIcons.Modules.SourceRoot;
    }
    if (ARTIFACT_ANDROID_TEST.equals(resolvedName)) {
      name = "AndroidTest";
      icon = AndroidTestRoot;
    }
    else if (ARTIFACT_UNIT_TEST.equals(resolvedName)) {
      name = "Test";
      icon = TestRoot;
    }
    myName = name;
    myIcon = icon;
    myResolvedModel = resolvedModel;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getResolvedName() {
    return myResolvedName;
  }

  @Override
  @Nullable
  public BaseArtifact getResolvedModel() {
    return myResolvedModel;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  @NotNull
  public AndroidGradleModel getGradleModel() {
    return getParent().getGradleModel();
  }

  @Override
  @NotNull
  public PsVariant getParent() {
    return (PsVariant)super.getParent();
  }

  @Override
  public boolean isEditable() {
    return false;
  }
}
