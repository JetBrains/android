/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class AndroidFacetType extends FacetType<AndroidFacet, AndroidFacetConfiguration> {

  public static final String TYPE_ID = "android";

  public AndroidFacetType() {
    super(AndroidFacet.ID, TYPE_ID, "Android");
  }


  @Override
  public AndroidFacetConfiguration createDefaultConfiguration() {
    return new AndroidFacetConfiguration();
  }

  @Override
  public AndroidFacet createFacet(@NotNull Module module,
                                  String name,
                                  @NotNull AndroidFacetConfiguration configuration,
                                  @Nullable Facet underlyingFacet) {
    // DO NOT COMMIT MODULE-ROOT MODELS HERE!
    // modules are not initialized yet, so some data may be lost

    return new AndroidFacet(module, name, configuration);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }

  @Override
  public Icon getIcon() {
    return AndroidIcons.AndroidModule;
  }
}
