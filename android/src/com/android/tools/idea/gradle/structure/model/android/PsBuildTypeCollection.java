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

import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PsBuildTypeCollection implements PsModelCollection<PsBuildType> {
  @NotNull private final Map<String, PsBuildType> myBuildTypesByName = Maps.newLinkedHashMap();
  @NotNull private final PsAndroidModule myParent;

  PsBuildTypeCollection(@NotNull PsAndroidModule parent) {
    myParent = parent;
    Map<String, BuildType> buildTypesFromGradle = Maps.newHashMap();
    for (BuildTypeContainer container : parent.getGradleModel().getAndroidProject().getBuildTypes()) {
      BuildType buildType = container.getBuildType();
      buildTypesFromGradle.put(buildType.getName(), buildType);
    }

    GradleBuildModel parsedModel = parent.getParsedModel();
    if (parsedModel != null) {
      AndroidModel android = parsedModel.android();
      if (android != null) {
        List<? extends BuildTypeModel> parsedBuildTypes = android.buildTypes();
        for (BuildTypeModel parsedBuildType : parsedBuildTypes) {
          String name = parsedBuildType.name();
          BuildType fromGradle = buildTypesFromGradle.remove(name);

          PsBuildType model = new PsBuildType(parent, fromGradle, parsedBuildType);
          myBuildTypesByName.put(name, model);
        }
      }
    }

    if (!buildTypesFromGradle.isEmpty()) {
      for (BuildType buildType : buildTypesFromGradle.values()) {
        PsBuildType model = new PsBuildType(parent, buildType, null);
        myBuildTypesByName.put(buildType.getName(), model);
      }
    }
  }

  @Override
  @Nullable
  public <S extends PsBuildType> S findElement(@NotNull String name, @NotNull Class<S> type) {
    if (PsBuildType.class.equals(type)) {
      return type.cast(myBuildTypesByName.get(name));
    }
    return null;
  }

  @Override
  public void forEach(@NotNull Consumer<PsBuildType> consumer) {
    myBuildTypesByName.values().forEach(consumer);
  }

  @NotNull
  public PsBuildType addNew(@NotNull String name) {
    assert myParent.getParsedModel() != null;
    AndroidModel androidModel = myParent.getParsedModel().android();
    assert androidModel != null;
    androidModel.addBuildType(name);
    List<BuildTypeModel> buildTypes = androidModel.buildTypes();
    PsBuildType model =
      new PsBuildType(myParent, null, buildTypes.stream().filter(it -> it.name().equals(name)).collect(MoreCollectors.onlyElement()));
    myBuildTypesByName.put(name, model);
    myParent.setModified(true);
    return model;
  }
}
