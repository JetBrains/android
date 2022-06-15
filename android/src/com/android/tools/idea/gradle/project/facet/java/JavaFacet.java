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

import static com.android.tools.idea.gradle.util.GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public class JavaFacet extends Facet<JavaFacetConfiguration> {
  @NotNull private static FacetTypeId<JavaFacet> TYPE_ID = new FacetTypeId<>("java-gradle");

  @NonNls public static final String TEST_CLASSES_TASK_NAME = "testClasses";
  @NonNls public static final String COMPILE_JAVA_TASK_NAME = "compileJava";

  private JavaModuleModel myJavaModuleModel;

  @Nullable
  public static JavaFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(ModuleSystemUtil.getHolderModule(module)).getFacetByType(TYPE_ID);
  }

  public JavaFacet(@NotNull Module module,
                   @NotNull String name,
                   @NotNull JavaFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static JavaFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType("java-gradle");
    assert facetType instanceof JavaFacetType;
    return (JavaFacetType)facetType;
  }

  @NotNull
  public static FacetTypeId<JavaFacet> getFacetTypeId() {
    return TYPE_ID;
  }

  @NotNull
  public static String getFacetId() {
    return "java-gradle";
  }

  @NotNull
  public static String getFacetName() {
    return "Java-Gradle";
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(JavaFacet.class);
  }

  @Nullable
  public String getGradleTaskName(@NotNull BuildMode buildMode) {
    if (!getConfiguration().BUILDABLE) {
      return null;
    }
    switch (buildMode) {
      case ASSEMBLE:
        return DEFAULT_ASSEMBLE_TASK_NAME;
      case COMPILE_JAVA:
        return COMPILE_JAVA_TASK_NAME;
      default:
        return null;
    }
  }

  public void setJavaModuleModel(@NotNull JavaModuleModel javaModuleModel) {
    myJavaModuleModel = javaModuleModel;
  }

  @Nullable
  public JavaModuleModel getJavaModuleModel() {
    return myJavaModuleModel;
  }
}
