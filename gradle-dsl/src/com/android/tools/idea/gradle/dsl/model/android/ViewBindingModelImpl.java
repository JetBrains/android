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

import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.ViewBindingModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.ViewBindingDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ViewBindingModelImpl extends GradleDslBlockModel implements ViewBindingModel {
  @NonNls public static final String ENABLED = "mEnabled";

  private AndroidModel androidModel;

  public ViewBindingModelImpl(@NotNull ViewBindingDslElement dslElement, @NotNull AndroidModel androidModel) {
    super(dslElement);
    this.androidModel = androidModel;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel enabled() {
    AndroidGradlePluginVersion version = myDslElement.getDslFile().getContext().getAgpVersion();
    if (version == null || version.compareTo(AndroidGradlePluginVersion.Companion.parse("4.0.0-alpha05")) < 0) {
      return getModelForProperty(ENABLED);
    }
    else if (version.compareTo(AndroidGradlePluginVersion.Companion.parse("7.0.0-alpha01")) >= 0) {
      return androidModel.buildFeatures().viewBinding();
    }
    else {
      GradleDslElement enabledElement = myDslElement.getPropertyElement(ENABLED);
      if (enabledElement == null) {
        return androidModel.buildFeatures().viewBinding();
      }
      else {
        return getModelForProperty(ENABLED);
      }
    }
  }
}
