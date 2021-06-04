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

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.settings.PluginsModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.PluginModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PluginsModelImpl extends GradleDslBlockModel implements PluginsModel {
  public static final @NonNls String APPLY = "apply";
  public static final @NonNls String ID = "id";
  public static final @NonNls String VERSION = "version";

  public PluginsModelImpl(@NotNull PluginsDslElement element) {
    super(element);
  }

  @Override
  public @NotNull List<PluginModel> plugins() {
    return new ArrayList<>(PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(myDslElement)).values());
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
    return new PluginModelImpl(literal, literal);
  }

  @Override
  public @NotNull PluginModel applyPlugin(@NotNull String plugin, @NotNull String version, boolean apply) {
    GradleDslInfixExpression expression = new GradleDslInfixExpression(myDslElement, null);
    GradleDslLiteral idLiteral = expression.setNewLiteral(ID, plugin.trim());
    expression.setNewLiteral(VERSION, version);
    expression.setNewLiteral(APPLY, apply);
    myDslElement.setNewElement(expression);
    return new PluginModelImpl(expression, idLiteral);
  }

  @Override
  public void removePlugin(@NotNull String plugin) {
    PluginModelImpl.removePlugins(PluginModelImpl.create(myDslElement), plugin);
  }
}
