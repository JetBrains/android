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
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.DefaultTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile.GradleDslVersionLiteral;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleVersionCatalogModelImpl implements GradleVersionCatalogModel {
  private Collection<GradleVersionCatalogFile> versionCatalogFiles;

  GradleVersionCatalogModelImpl(@NotNull Collection<GradleVersionCatalogFile> versionCatalogFile) {
    this.versionCatalogFiles = versionCatalogFile;
  }

  private Map<String, ExtModel> extractByName(String sectionName) {
    return versionCatalogFiles.stream().collect(Collectors.toMap(GradleVersionCatalogFile::getCatalogName, myGradleDslFile -> {
      GradleDslExpressionMap librariesDslElement = myGradleDslFile.ensurePropertyElement(
        new PropertiesElementDescription<>(sectionName, GradleDslExpressionMap.class, GradleDslExpressionMap::new)
      );
      return new ExtModelImpl(librariesDslElement, GradleVersionCatalogPropertyModel::new, GradleVersionCatalogPropertyModel::new);
    }));
  }

  @Override
  public ExtModel libraries(String catalogName) {
    return extractByName("libraries").get(catalogName);
  }

  @Override
  public ExtModel plugins(String catalogName) {
    return extractByName("plugins").get(catalogName);
  }

  @Override
  public ExtModel versions(String catalogName) {
    return extractByName("versions").get(catalogName);
  }

  @Override
  public ExtModel bundles(String catalogName) {
    return extractByName("bundles").get(catalogName);
  }

  public Set<String> catalogNames(){
    return versionCatalogFiles.stream().map(GradleVersionCatalogFile::getCatalogName).collect(Collectors.toSet());
  }

  class GradleVersionCatalogPropertyModel extends GradlePropertyModelImpl {
    public GradleVersionCatalogPropertyModel(@NotNull GradleDslElement element) {
      super(element);
    }

    public GradleVersionCatalogPropertyModel(@NotNull GradleDslElement holder, @NotNull PropertyType type, @NotNull String name) {
      super(holder, type, name);
    }

    @NotNull
    @Override
    public GradlePropertyModel addListValue(){
      if (!"bundles".equals(myPropertyHolder.getName())) {
        return super.addListValue();
      }
      GradlePropertyModelImpl model = (GradlePropertyModelImpl)super.addListValue();
      model.addTransform(new LibraryTransform());
      return model;
    }

    @NotNull
    @Override
    public GradlePropertyModel addListValueAt(int index) {
      if (!"bundles".equals(myPropertyHolder.getName())) {
        return super.addListValueAt(index);
      }
      GradlePropertyModelImpl model = (GradlePropertyModelImpl)super.addListValueAt(index);
      model.addTransform(new LibraryTransform());
      return model;
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

  static class LibraryTransform extends DefaultTransform {
    @Override
    public @NotNull GradleDslExpression bind(@NotNull GradleDslElement holder,
                                             @Nullable GradleDslElement oldElement,
                                             @NotNull Object value,
                                             @NotNull String name) {
      // prop.addListValue() returns element with empty name
      if (oldElement != null &&
          oldElement.getName().isEmpty() &&
          value instanceof ReferenceTo) {
        GradleDslLiteral literal = new GradleDslLiteral(holder, GradleNameElement.fake(name));
        literal.setValue(((ReferenceTo)value).getReferredElement().getName());
        return literal;
      }
      return super.bind(holder, oldElement, value, name);
    }
  }
}
