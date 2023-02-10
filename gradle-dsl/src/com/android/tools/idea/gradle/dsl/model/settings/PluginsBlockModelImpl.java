/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.settings;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.model.PluginModelImpl.ALIAS;

import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginsBlockModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.PluginModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PluginsBlockModelImpl extends GradleDslBlockModel implements PluginsBlockModel {
  public static final @NonNls String APPLY = "apply";
  public static final @NonNls String ID = "id";
  public static final @NonNls String VERSION = "version";

  public PluginsBlockModelImpl(@NotNull PluginsDslElement element) {
    super(element);
  }

  @Override
  public @NotNull List<PluginModel> plugins() {
    return new ArrayList<>(PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(myDslElement)).values());
  }

  @Override
  public @NotNull List<PluginModel> appliedPlugins() {
    Predicate<PluginModel> appliedPredicate = (plugin) -> {
      ResolvedPropertyModel apply = plugin.apply();
      ValueType valueType = apply.getValueType();
      if (valueType == NONE) {
        // pluginManagement.plugins defaults to `apply false`
        return false;
      }
      else if (valueType == BOOLEAN) {
        return apply.getValue(BOOLEAN_TYPE);
      }
      else {
        // not understood: default to not applied.
        return false;
      }
    };
    return plugins().stream().filter(appliedPredicate).collect(Collectors.toList());
  }

  @Override
  public @NotNull PluginModel applyPlugin(@NotNull String plugin) {
    Map<String, PluginModelImpl> models = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(myDslElement));
    if (models.containsKey(plugin)) {
      return models.get(plugin);
    }
    GradleDslLiteral literal = new GradleDslLiteral(myDslElement, GradleNameElement.create(ID));
    literal.setElementType(REGULAR);
    literal.setValue(plugin.trim());
    myDslElement.setNewElement(literal);
    return new PluginModelImpl(literal);
  }

  @Override
  public @NotNull PluginModel applyPlugin(@NotNull String plugin, @NotNull String version, @Nullable Boolean apply) {
    GradleDslInfixExpression expression = new GradleDslInfixExpression(myDslElement, null);
    expression.setNewLiteral(ID, plugin.trim());
    expression.setNewLiteral(VERSION, version);
    if (apply != null) {
      expression.setNewLiteral(APPLY, apply);
    }
    myDslElement.setNewElement(expression);
    return new PluginModelImpl(expression);
  }

  @Override
  public @NotNull PluginModel applyPlugin(@NotNull ReferenceTo reference, @Nullable Boolean apply) {
    // note: reparented if apply is non-null
    GradleDslMethodCall alias = new GradleDslMethodCall(myDslElement, GradleNameElement.empty(), ALIAS);
    GradleDslLiteral target = new GradleDslLiteral(alias.getArgumentsElement(), GradleNameElement.empty());
    target.setValue(reference);
    alias.addNewArgument(target);
    if (apply != null) {
      GradleDslInfixExpression expression = new GradleDslInfixExpression(myDslElement, null);
      alias.setParent(expression);
      expression.setNewElement(alias);
      expression.setNewLiteral(APPLY, apply);
      myDslElement.setNewElement(expression);
      return new PluginModelImpl(expression);
    }
    else {
      myDslElement.setNewElement(alias);
      return new PluginModelImpl(alias);
    }
  }

  @Override
  public void removePlugin(@NotNull String plugin) {
    PluginModelImpl.removePlugins(PluginModelImpl.create(myDslElement), plugin);
  }
}
