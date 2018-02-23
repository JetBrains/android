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

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.FILE_TRANSFORM;

public class SigningConfigModelImpl extends GradleDslBlockModel implements SigningConfigModel {
  @NonNls private static final String STORE_FILE = "storeFile";
  @NonNls private static final String STORE_PASSWORD = "storePassword";
  @NonNls private static final String STORE_TYPE = "storeType";
  @NonNls private static final String KEY_ALIAS = "keyAlias";
  @NonNls private static final String KEY_PASSWORD = "keyPassword";


  public SigningConfigModelImpl(@NotNull SigningConfigDslElement dslElement) {
    super(dslElement);
    myDslElement = dslElement;
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel storeFile() {
    return GradlePropertyModelBuilder.create(myDslElement, STORE_FILE).asMethod(true)
      .addTransform(FILE_TRANSFORM).buildResolved();
  }

  @Override
  @NotNull
  public PasswordPropertyModel storePassword() {
    return GradlePropertyModelBuilder.create(myDslElement, STORE_PASSWORD).asMethod(true)
      .buildPassword();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel storeType() {
    return getModelForProperty(STORE_TYPE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel keyAlias() {
    return getModelForProperty(KEY_ALIAS);
  }

  @Override
  @NotNull
  public PasswordPropertyModel keyPassword() {
    return GradlePropertyModelBuilder.create(myDslElement, KEY_PASSWORD).asMethod(true)
      .buildPassword();
  }
}
