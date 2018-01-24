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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE;

/**
 * Represents a fully resolved property, that is it takes a property and provides an interface that squishes any references.
 * This for for use in models other than the Ext model, since in these we do not care about the reference chain of the property
 * and just want to get the actual value. Using {@link GradlePropertyModelImpl} directly can result in having to follow long
 * reference changes in order to get a value.
 */
public class ResolvedPropertyModelImpl implements ResolvedPropertyModel {
  @NotNull private final GradlePropertyModel myRealModel;

  public ResolvedPropertyModelImpl(@NotNull GradlePropertyModel realModel) {
    myRealModel = realModel;
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return resolveModel().getValueType();
  }

  @NotNull
  @Override
  public PropertyType getPropertyType() {
    return myRealModel.getPropertyType();
  }

  @Nullable
  @Override
  public <T> T getValue(@NotNull TypeReference<T> typeReference) {
    return resolveModel().getValue(typeReference);
  }

  @Nullable
  @Override
  public <T> T getRawValue(@NotNull TypeReference<T> typeReference) {
    return myRealModel.getRawValue(typeReference);
  }

  @NotNull
  @Override
  public List<GradlePropertyModel> getDependencies() {
    return myRealModel.getDependencies();
  }

  @NotNull
  @Override
  public String getName() {
    return myRealModel.getName();
  }

  @NotNull
  @Override
  public String getFullyQualifiedName() {
    return myRealModel.getFullyQualifiedName();
  }

  @NotNull
  @Override
  public VirtualFile getGradleFile() {
    return myRealModel.getGradleFile();
  }

  @Override
  public void setValue(@NotNull Object value) {
    myRealModel.setValue(value);
  }

  @Override
  public ResolvedPropertyModel convertToEmptyMap() {
    myRealModel.convertToEmptyMap();
    return this;
  }

  @Override
  public GradlePropertyModel addMapValue(@NotNull String key) {
    return myRealModel.addMapValue(key);
  }

  @Override
  public GradlePropertyModel convertToEmptyList() {
    myRealModel.convertToEmptyList();
    return this;
  }

  @Override
  public GradlePropertyModel addListValue() {
    return myRealModel.addListValue();
  }

  @Override
  public GradlePropertyModel addListValueAt(int index) {
    return myRealModel.addListValueAt(index);
  }

  @Override
  public void delete() {
    myRealModel.delete();
  }

  @Override
  public ResolvedPropertyModel resolve() {
    return this;
  }

  @Override
  @NotNull
  public GradlePropertyModel getUnresolvedModel() {
    return myRealModel;
  }

  @NotNull
  @Override
  public GradlePropertyModel getResultModel() {
    return resolveModel();
  }

  private GradlePropertyModel resolveModel() {
    GradlePropertyModel model = myRealModel;
    Set<GradlePropertyModel> seenModels = new HashSet<>();

    while (model.getValueType() == REFERENCE && !seenModels.contains(model)) {
      if (model.getDependencies().isEmpty()) {
        return model;
      }
      seenModels.add(model);
      model = model.getDependencies().get(0);
    }
    return model;
  }
}
