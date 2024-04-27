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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;

import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.InexpressiblePropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.InfixPropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.LiteralToInfixTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.MapPropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PluginAliasTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PluginNameTransform;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PluginModelImpl implements PluginModel {
  @NonNls public static final String ALIAS = "alias";
  @NonNls public static final String APPLY = "apply";
  @NonNls public static final String ID = "id";
  @NonNls public static final String KOTLIN = "kotlin";
  @NonNls public static final String PLUGIN = "plugin";
  @NonNls public static final String VERSION = "version";

  @NotNull
  private final GradleDslElement myCompleteElement;

  @NotNull
  public static List<PluginModelImpl> create(@NotNull GradlePropertiesDslElement dslElement) {
    List<GradleDslElement> elements = dslElement.getAllPropertyElements();
    List<PluginModelImpl> results = new ArrayList<>();

    for (GradleDslElement e : elements) {
      if (e instanceof GradleDslSimpleExpression || e instanceof GradleDslExpressionMap || e instanceof GradleDslInfixExpression) {
        results.add(new PluginModelImpl(e));
      }
      else if (e instanceof GradleDslExpressionList) {
        GradleDslExpressionList element = (GradleDslExpressionList)e;
        for (GradleDslSimpleExpression item : element.getSimpleExpressions()) {
          results.add(new PluginModelImpl(item));
        }
      }
    }

    return results;
  }

  public static Map<String, PluginModelImpl> deduplicatePlugins(@NotNull List<PluginModelImpl> models) {
    Map<String, PluginModelImpl> modelMap = new LinkedHashMap<>();
    for (PluginModelImpl model : models) {
      ResolvedPropertyModel propertyModel = model.name();
      if (propertyModel.getValueType() == STRING) {
        modelMap.put(propertyModel.forceString(), model);
      }
    }
    return modelMap;
  }

  public static void removePlugins(@NotNull List<PluginModelImpl> models, @NotNull String name) {
    for (PluginModelImpl model : models) {
      if (name.equals(model.name().toString())) {
        model.remove();
      }
    }
  }

  public PluginModelImpl(@NotNull GradleDslElement completeElement) {
    myCompleteElement = completeElement;
  }

  @NotNull
  @Override
  public ResolvedPropertyModel name() {
    return GradlePropertyModelBuilder.create(myCompleteElement)
      .addTransform(new PluginAliasTransform(ID, ArtifactDependencySpec::getName, ArtifactDependencySpecImpl::setName))
      .addTransform(new PluginNameTransform())
      .addLanguageTransform(GradleDslNameConverter.Kind.DECLARATIVE_TOML , new MapPropertyTransform(ID))
      .buildResolved();
  }

  @NotNull
  @Override
  public ResolvedPropertyModel version() {
    return GradlePropertyModelBuilder.create(myCompleteElement)
      .addTransform(new PluginAliasTransform(VERSION, ArtifactDependencySpec::getVersion, ArtifactDependencySpecImpl::setVersion))
      .addTransform(new LiteralToInfixTransform(VERSION))
      .addTransform(new InfixPropertyTransform(VERSION))
      .addLanguageTransform(GradleDslNameConverter.Kind.DECLARATIVE_TOML , new MapPropertyTransform(VERSION))
      .addTransform(new InexpressiblePropertyTransform())
      .buildResolved();
  }

  @NotNull
  @Override
  public ResolvedPropertyModel apply() {
    return GradlePropertyModelBuilder.create(myCompleteElement)
      .addTransform(new LiteralToInfixTransform(APPLY))
      .addTransform(new InfixPropertyTransform(APPLY))
      .addLanguageTransform(GradleDslNameConverter.Kind.DECLARATIVE_TOML , new MapPropertyTransform(APPLY))
      .addTransform(new InexpressiblePropertyTransform())
      .buildResolved();
  }

  @Override
  public void remove() {
    PropertyUtil.removeElement(myCompleteElement);
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    return myCompleteElement.getPsiElement();
  }

}
