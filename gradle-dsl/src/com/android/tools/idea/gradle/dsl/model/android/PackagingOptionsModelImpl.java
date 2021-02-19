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
package com.android.tools.idea.gradle.dsl.model.android;

import static com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.DexDslElement.DEX;
import static com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.JniLibsDslElement.JNI_LIBS;
import static com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.ResourcesDslElement.RESOURCES;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET;

import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.DexModel;
import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.JniLibsModel;
import com.android.tools.idea.gradle.dsl.api.android.packagingOptions.ResourcesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.packagingOptions.DexModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.packagingOptions.JniLibsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.packagingOptions.ResourcesModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.DexDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.JniLibsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.packagingOptions.ResourcesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PackagingOptionsModelImpl extends GradleDslBlockModel implements PackagingOptionsModel {
  @NonNls public static final ModelPropertyDescription DO_NOT_STRIP = new ModelPropertyDescription("mDoNotStrip", MUTABLE_SET);
  @NonNls public static final ModelPropertyDescription EXCLUDES = new ModelPropertyDescription("mExcludes", MUTABLE_SET);
  @NonNls public static final ModelPropertyDescription MERGES = new ModelPropertyDescription("mMerges", MUTABLE_SET);
  @NonNls public static final ModelPropertyDescription PICK_FIRSTS = new ModelPropertyDescription("mPickFirsts", MUTABLE_SET);

  public PackagingOptionsModelImpl(@NotNull PackagingOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel doNotStrip() {
    return getModelForProperty(DO_NOT_STRIP);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel excludes() {
    return getModelForProperty(EXCLUDES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel merges() {
    return getModelForProperty(MERGES);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel pickFirsts() {
    return getModelForProperty(PICK_FIRSTS);
  }

  @Override
  public @NotNull DexModel dex() {
    DexDslElement element = myDslElement.ensurePropertyElement(DEX);
    return new DexModelImpl(element);
  }

  @Override
  public @NotNull JniLibsModel jniLibs() {
    JniLibsDslElement element = myDslElement.ensurePropertyElement(JNI_LIBS);
    return new JniLibsModelImpl(element);
  }

  @Override
  public @NotNull ResourcesModel resources() {
    ResourcesDslElement element = myDslElement.ensurePropertyElement(RESOURCES);
    return new ResourcesModelImpl(element);
  }
}
