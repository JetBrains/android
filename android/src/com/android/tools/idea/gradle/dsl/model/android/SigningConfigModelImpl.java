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
import com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.ElementBindingFunction;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.ElementTransform;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.TransformCondition;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createOrReplaceBasicExpression;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.defaultElementTransform;

public class SigningConfigModelImpl extends GradleDslBlockModel implements SigningConfigModel {
  @NonNls private static final String STORE_FILE = "storeFile";
  @NonNls private static final String STORE_FILE_METHOD = "file";
  @NonNls private static final String STORE_PASSWORD = "storePassword";
  @NonNls private static final String STORE_TYPE = "storeType";
  @NonNls private static final String KEY_ALIAS = "keyAlias";
  @NonNls private static final String KEY_PASSWORD = "keyPassword";

  @NotNull
  private static final TransformCondition STORE_FILE_COND = e -> e == null || e instanceof GradleDslMethodCall;

  @NotNull
  private static final ElementTransform STORE_FILE_ELEMENT_TRANSFORM = e -> {
    if (STORE_FILE_COND.test(e)) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
      if (methodCall.getArguments().isEmpty()) {
        return null;
      }
      GradleDslElement arg = methodCall.getArguments().get(0);
      if (arg instanceof GradleDslExpression) {
        return (GradleDslExpression)arg;
      }
      return null;
    }
    else {
      return defaultElementTransform.transform(e);
    }
  };

  @NotNull
  private static final ElementBindingFunction STORE_FILE_BINDING = (holder, oldElement, value, name) -> {
    if (oldElement == null) {
      // No element currently exists.
      GradleDslMethodCall methodCall = new GradleDslMethodCall(holder, GradleNameElement.create(name), STORE_FILE_METHOD);
      GradleDslExpression literal = createOrReplaceBasicExpression(methodCall, null, value, GradleNameElement.empty());
      methodCall.addNewArgument(literal);
      return methodCall;
    }
    else {
      // Replace the argument.
      GradleDslMethodCall methodCall = (GradleDslMethodCall)oldElement;
      GradleDslElement element = STORE_FILE_ELEMENT_TRANSFORM.transform(methodCall);
      GradleDslExpression expression = createOrReplaceBasicExpression(methodCall, element, value, GradleNameElement.empty());
      if (element != expression) {
        // We needed to create a new element, replace the old one.
        methodCall.remove(element);
        methodCall.addNewArgument(expression);
      }
      return methodCall;
    }
  };

  @NotNull private static final PropertyTransform STORE_FILE_TRANSFORM =
    new PropertyTransform(STORE_FILE_COND, STORE_FILE_ELEMENT_TRANSFORM, STORE_FILE_BINDING);


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
      .addTransform(STORE_FILE_TRANSFORM).buildResolved();
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
