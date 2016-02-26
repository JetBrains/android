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
import com.android.tools.idea.gradle.structure.model.PsdChildModel;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.builder.model.AndroidProject.*;
import static com.intellij.icons.AllIcons.Modules.TestRoot;
import static com.intellij.icons.AllIcons.Nodes.Artifact;
import static icons.AndroidIcons.AndroidTestRoot;

public class PsdAndroidArtifactModel extends PsdChildModel {
  @NotNull private final String myName;
  @NotNull private final Icon myIcon;
  @NotNull private final BaseArtifact myGradleModel;

  PsdAndroidArtifactModel(@NotNull PsdVariantModel parent, @NotNull BaseArtifact gradleModel) {
    super(parent);
    Icon icon = Artifact;
    String name = gradleModel.getName();
    if (ARTIFACT_MAIN.equals(name)) {
      name = "";
      icon = AllIcons.Modules.SourceRoot;
    }
    if (ARTIFACT_ANDROID_TEST.equals(name)) {
      name = "AndroidTest";
      icon = AndroidTestRoot;
    }
    else if (ARTIFACT_UNIT_TEST.equals(name)) {
      name = "Test";
      icon = TestRoot;
    }
    myName = name;
    myIcon = icon;
    myGradleModel = gradleModel;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public BaseArtifact getGradleModel() {
    return myGradleModel;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  @NotNull
  public PsdVariantModel getParent() {
    return (PsdVariantModel)super.getParent();
  }

  @Override
  public boolean isEditable() {
    return false;
  }
}
