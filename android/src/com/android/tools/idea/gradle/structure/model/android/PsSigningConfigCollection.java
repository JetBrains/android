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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.SigningConfig;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PsSigningConfigCollection implements PsModelCollection<PsSigningConfig> {
  @NotNull private final Map<String, PsSigningConfig> mySigningConfigsByName = Maps.newLinkedHashMap();
  @NotNull private final PsAndroidModule myParent;

  PsSigningConfigCollection(@NotNull PsAndroidModule parent) {
    myParent = parent;
    Map<String, SigningConfig> signingConfigsFromGradle = Maps.newHashMap();
    for (SigningConfig signingConfig : parent.getGradleModel().getAndroidProject().getSigningConfigs()) {
      signingConfigsFromGradle.put(signingConfig.getName(), signingConfig);
    }

    GradleBuildModel parsedModel = parent.getParsedModel();
    if (parsedModel != null) {
      AndroidModel android = parsedModel.android();
      if (android != null) {
        List<SigningConfigModel> parsedSigningConfigs = android.signingConfigs();
        for (SigningConfigModel parsedSigningConfig : parsedSigningConfigs) {
          String name = parsedSigningConfig.name();
          SigningConfig fromGradle = signingConfigsFromGradle.remove(name);

          PsSigningConfig model = new PsSigningConfig(parent, fromGradle, parsedSigningConfig);
          mySigningConfigsByName.put(name, model);
        }
      }
    }

    if (!signingConfigsFromGradle.isEmpty()) {
      for (SigningConfig signingConfig : signingConfigsFromGradle.values()) {
        PsSigningConfig model = new PsSigningConfig(parent, signingConfig, null);
        mySigningConfigsByName.put(signingConfig.getName(), model);
      }
    }
  }

  @Override
  @Nullable
  @SuppressWarnings("TypeParameterExtendsFinalClass")
  public <S extends PsSigningConfig> S findElement(@NotNull String name, @NotNull Class<S> type) {
    if (PsSigningConfig.class.equals(type)) {
      return type.cast(mySigningConfigsByName.get(name));
    }
    return null;
  }

  @Override
  public void forEach(@NotNull Consumer<PsSigningConfig> consumer) {
    mySigningConfigsByName.values().forEach(consumer);
  }

  @NotNull
  public PsSigningConfig addNew(String name) {
    assert myParent.getParsedModel() != null;
    AndroidModel androidModel = myParent.getParsedModel().android();
    assert androidModel != null;

    androidModel.addSigningConfig(name);
    List<SigningConfigModel> signingConfigs = androidModel.signingConfigs();
    PsSigningConfig model =
      new PsSigningConfig(myParent, null,
                          signingConfigs.stream().filter(it -> it.name().equals(name)).collect(MoreCollectors.onlyElement()));
    mySigningConfigsByName.put(name, model);
    myParent.setModified(true);
    return model;
  }

  public void remove(@NotNull String name) {
    assert myParent.getParsedModel() != null;
    AndroidModel androidModel = myParent.getParsedModel().android();
    assert androidModel != null;
    androidModel.removeSigningConfig(name);
    mySigningConfigsByName.remove(name);
    myParent.setModified(true);
  }
}
