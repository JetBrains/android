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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.KEY_ALIAS;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.KEY_PASSWORD;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_FILE;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_PASSWORD;
import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_TYPE;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SigningConfigDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<SigningConfigDslElement> SIGNING_CONFIG =
    new PropertiesElementDescription<>(null,
                                       SigningConfigDslElement.class,
                                       SigningConfigDslElement::new,
                                       SigningConfigDslElementSchema::new);

  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"keyAlias", property, KEY_ALIAS, VAR},
    {"setKeyAlias", exactly(1), KEY_ALIAS, SET},
    {"keyPassword", property, KEY_PASSWORD, VAR},
    {"setKeyPassword", exactly(1), KEY_PASSWORD, SET},
    {"storeFile", property, STORE_FILE, VAR},
    {"setStoreFile", exactly(1), STORE_FILE, SET},
    {"storePassword", property, STORE_PASSWORD, VAR},
    {"setStorePassword", exactly(1), STORE_PASSWORD, SET},
    {"storeType", property, STORE_TYPE, VAR},
    {"setStoreType", exactly(1), STORE_TYPE, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"keyAlias", property, KEY_ALIAS, VAR},
    {"keyAlias", exactly(1), KEY_ALIAS, SET},
    {"keyPassword", property, KEY_PASSWORD, VAR},
    {"keyPassword", exactly(1), KEY_PASSWORD, SET},
    {"storeFile", property, STORE_FILE, VAR},
    {"storeFile", exactly(1), STORE_FILE, SET},
    {"storePassword", property, STORE_PASSWORD, VAR},
    {"storePassword", exactly(1), STORE_PASSWORD, SET},
    {"storeType", property, STORE_TYPE, VAR},
    {"storeType", exactly(1), STORE_TYPE, SET},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"keyAlias", property, KEY_ALIAS, VAR},
    {"keyPassword", property, KEY_PASSWORD, VAR},
    {"storeFile", property, STORE_FILE, VAR},
    {"storePassword", property, STORE_PASSWORD, VAR},
    {"storeType", property, STORE_TYPE, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  @Nullable
  private String methodName;

  @Override
  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  @Override
  public String getMethodName() {
    return  methodName;
  }

  public SigningConfigDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // the debug signingConfig is automatically created
    return getName().equals("debug");
  }

  public static final class SigningConfigDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    @NotNull
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @Nullable
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.com.android.build.api.dsl";
    }
  }
}
