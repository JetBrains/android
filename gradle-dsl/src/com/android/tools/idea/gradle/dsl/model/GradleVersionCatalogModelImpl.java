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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.DefaultTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile.GradleDslVersionLiteral;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleVersionCatalogModelImpl extends GradleFileModelImpl implements GradleVersionCatalogModel {

  GradleVersionCatalogModelImpl(@NotNull GradleVersionCatalogFile versionCatalogFile) {
    super(versionCatalogFile);
  }

  @Override
  public @NotNull ExtModel libraries() {
    GradleDslExpressionMap librariesDslElement = myGradleDslFile.ensurePropertyElement(
      new PropertiesElementDescription<>("libraries", GradleDslExpressionMap.class, GradleDslExpressionMap::new)
    );
    return new ExtModelImpl(librariesDslElement, GradleVersionCatalogPropertyModel::new, GradleVersionCatalogPropertyModel::new);
  }

  @Override
  public @NotNull ExtModel plugins() {
    GradleDslExpressionMap pluginsDslElement = myGradleDslFile.ensurePropertyElement(
      new PropertiesElementDescription<>("plugins", GradleDslExpressionMap.class, GradleDslExpressionMap::new)
    );
    return new ExtModelImpl(pluginsDslElement, GradleVersionCatalogPropertyModel::new, GradleVersionCatalogPropertyModel::new);
  }

  @Override
  public @NotNull ExtModel versions() {
    GradleDslExpressionMap versionsDslElement = myGradleDslFile.ensurePropertyElementAt(
      new PropertiesElementDescription<>("versions", GradleDslExpressionMap.class, GradleDslExpressionMap::new), 0);
    return new ExtModelImpl(versionsDslElement);
  }

  class GradleVersionCatalogPropertyModel extends GradlePropertyModelImpl {
    public GradleVersionCatalogPropertyModel(@NotNull GradleDslElement element) {
      super(element);
    }

    public GradleVersionCatalogPropertyModel(@NotNull GradleDslElement holder, @NotNull PropertyType type, @NotNull String name) {
      super(holder, type, name);
    }

    @Override
    public @NotNull GradlePropertyModel getMapValue(@NotNull String key) {
      if (!"version".equals(key)) {
        return super.getMapValue(key);
      }
      GradlePropertyModelImpl model = (GradlePropertyModelImpl)super.getMapValue(key);
      model.addTransform(new VersionTransform());
      return model;
    }
  }

  static class VersionTransform extends DefaultTransform {
    @Override
    public @NotNull GradleDslExpression bind(@NotNull GradleDslElement holder,
                                             @Nullable GradleDslElement oldElement,
                                             @NotNull Object value,
                                             @NotNull String name) {
      if (oldElement == null) {
        GradleDslVersionLiteral literal = new GradleDslVersionLiteral(holder, GradleNameElement.fake(name), value);
        literal.setValue(value);
        return literal;
      }
      return super.bind(holder, oldElement, value, name);
    }
  }
}
