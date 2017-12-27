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

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.JavaArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeBaseArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeJavaArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public class PsAndroidArtifactCollection implements PsModelCollection<PsAndroidArtifact> {
  @NotNull private final PsVariant myParent;
  @NotNull private final Map<String, PsAndroidArtifact> myArtifactsByName = Maps.newHashMap();

  PsAndroidArtifactCollection(@NotNull PsVariant parent) {
    myParent = parent;
    IdeVariant variant = myParent.getResolvedModel();
    if (variant != null) {
      addArtifact(variant.getMainArtifact());
      for (AndroidArtifact androidArtifact : variant.getExtraAndroidArtifacts()) {
        if (androidArtifact != null) {
          addArtifact((IdeAndroidArtifact)androidArtifact);
        }
      }
      for (JavaArtifact javaArtifact : variant.getExtraJavaArtifacts()) {
        if (javaArtifact != null) {
          addArtifact((IdeJavaArtifact)javaArtifact);
        }
      }
    }
  }

  private void addArtifacts(@NotNull Collection<? extends IdeBaseArtifact> artifacts) {
    artifacts.forEach(this::addArtifact);
  }

  private void addArtifact(@NotNull IdeBaseArtifact artifact) {
    myArtifactsByName.put(artifact.getName(), new PsAndroidArtifact(myParent, artifact.getName(), artifact));
  }

  @Override
  @Nullable
  public <S extends PsAndroidArtifact> S findElement(@NotNull String name, @NotNull Class<S> type) {
    PsAndroidArtifact found = myArtifactsByName.get(name);
    return type.isInstance(found) ? type.cast(found) : null;
  }

  @Override
  public void forEach(@NotNull Consumer<PsAndroidArtifact> consumer) {
    myArtifactsByName.values().forEach(consumer);
  }

  @NotNull
  public PsVariant getParent() {
    return myParent;
  }
}
