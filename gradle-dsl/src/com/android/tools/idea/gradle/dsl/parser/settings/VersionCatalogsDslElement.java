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
package com.android.tools.idea.gradle.dsl.parser.settings;

import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel;
import com.android.tools.idea.gradle.dsl.model.settings.VersionCatalogModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class VersionCatalogsDslElement extends GradleDslElementMap implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<VersionCatalogsDslElement> VERSION_CATALOGS =
    new PropertiesElementDescription<>("versionCatalogs", VersionCatalogsDslElement.class, VersionCatalogsDslElement::new);

  @Override
  public PropertiesElementDescription getChildPropertiesElementDescription(GradleDslNameConverter converter, String name) {
    return VersionCatalogDslElement.VERSION_CATALOG;
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    return name.equals("libs");
  }

  public VersionCatalogsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
    VersionCatalogDslElement libs = new VersionCatalogDslElement(this, GradleNameElement.fake("libs"));
    addDefaultProperty(libs);
  }

  public @NotNull List<VersionCatalogModel> get() {
    List<VersionCatalogModel> result = new ArrayList<>();
    for (VersionCatalogDslElement dslElement : getValues(VersionCatalogDslElement.class)) {
      result.add(new VersionCatalogModelImpl(dslElement));
    }
    return result;
  }
}
