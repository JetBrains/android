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
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModel;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public class PsBuildTypeCollection implements PsModelCollection<PsBuildType> {
  @NotNull private final Map<String, PsBuildType> myBuildTypesByName = Maps.newHashMap();

  PsBuildTypeCollection(@NotNull PsAndroidModule parent) {
    Map<String, BuildType> buildTypesFromGradle = Maps.newHashMap();
    for (BuildTypeContainer container : parent.getGradleModel().getAndroidProject().getBuildTypes()) {
      BuildType buildType = container.getBuildType();
      buildTypesFromGradle.put(buildType.getName(), buildType);
    }

    GradleBuildModel parsedModel = parent.getParsedModel();
    if (parsedModel != null) {
      Collection<BuildTypeModel> parsedBuildTypes = parsedModel.android().buildTypes();
      for (BuildTypeModel parsedBuildType : parsedBuildTypes) {
        String name = parsedBuildType.name();
        BuildType fromGradle = buildTypesFromGradle.remove(name);

        PsBuildType model = new PsBuildType(parent, fromGradle, parsedBuildType);
        myBuildTypesByName.put(name, model);
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
}
