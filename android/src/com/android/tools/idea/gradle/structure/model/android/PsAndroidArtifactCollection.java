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
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.structure.model.PsModelCollection;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class PsAndroidArtifactCollection implements PsModelCollection<PsAndroidArtifact> {
  @NotNull private final PsVariant myParent;
  @NotNull private final Map<String, PsAndroidArtifact> myArtifactsByName = Maps.newHashMap();

  PsAndroidArtifactCollection(@NotNull PsVariant parent) {
    myParent = parent;
    Variant variant = myParent.getResolvedModel();
    if (variant != null) {
      addArtifact(variant.getMainArtifact());
      addArtifacts(variant.getExtraAndroidArtifacts());
      addArtifacts(variant.getExtraJavaArtifacts());
    }
  }

  private void addArtifacts(@NotNull Collection<? extends BaseArtifact> artifacts) {
    for (BaseArtifact artifact : artifacts) {
      addArtifact(artifact);
    }
  }

  private void addArtifact(@NotNull BaseArtifact artifact) {
    myArtifactsByName.put(artifact.getName(), new PsAndroidArtifact(myParent, artifact.getName(), artifact));
  }

  @Override
  @NotNull
  public List<PsAndroidArtifact> getElements() {
    return Lists.newArrayList(myArtifactsByName.values());
  }

  @Override
  @Nullable
  public <S extends PsAndroidArtifact> S findElement(@NotNull String name, @NotNull Class<S> type) {
    PsAndroidArtifact found = myArtifactsByName.get(name);
    return type.isInstance(found) ? type.cast(found) : null;
  }

  @NotNull
  public PsVariant getParent() {
    return myParent;
  }
}
