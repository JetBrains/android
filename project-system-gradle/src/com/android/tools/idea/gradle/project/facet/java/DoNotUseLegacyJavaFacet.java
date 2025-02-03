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
package com.android.tools.idea.gradle.project.facet.java;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * Java-Gradle facet.
 * <p>
 * This facet is applied to these type of modules in Gradle-based Android projects:
 * <ul>
 * <li>Java library modules</li>
 * <li>non-Android, top-level modules that represent an IDEA project (if applicable)</li>
 * </ul>
 * </p>
 */
@Deprecated
public class DoNotUseLegacyJavaFacet extends Facet<JavaFacetConfiguration> {
  @NotNull static FacetTypeId<DoNotUseLegacyJavaFacet> TYPE_ID = new FacetTypeId<>("java-gradle");

  public DoNotUseLegacyJavaFacet(@NotNull Module module,
                                 @NotNull String name,
                                 @NotNull JavaFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  private static JavaFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType("java-gradle");
    assert facetType instanceof JavaFacetType;
    return (JavaFacetType)facetType;
  }

  @NotNull
  public static String getFacetId() {
    return "java-gradle";
  }

  @NotNull
  public static String getFacetName() {
    return "Java-Gradle";
  }


}
