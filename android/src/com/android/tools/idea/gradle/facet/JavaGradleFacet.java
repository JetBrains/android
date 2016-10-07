/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.facet;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleBuilds;
import com.intellij.facet.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ProjectTopics.PROJECT_ROOTS;
import static com.intellij.facet.impl.FacetUtil.saveFacetConfiguration;

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
public class JavaGradleFacet extends Facet<JavaGradleFacetConfiguration> {
  private static final Logger LOG = Logger.getInstance(JavaGradleFacet.class);

  @NotNull public static FacetTypeId<JavaGradleFacet> TYPE_ID = new FacetTypeId<>("java-gradle");

  @NonNls public static final String TEST_CLASSES_TASK_NAME = "testClasses";
  @NonNls public static final String COMPILE_JAVA_TASK_NAME = "compileJava";

  @NonNls public static final String ID = "java-gradle";
  @NonNls public static final String NAME = "Java-Gradle";

  private JavaProject myJavaProject;

  @Nullable
  public static JavaGradleFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(TYPE_ID);
  }

  public JavaGradleFacet(@NotNull Module module,
                         @NotNull String name,
                         @NotNull JavaGradleFacetConfiguration configuration) {
    //noinspection ConstantConditions
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static JavaGradleFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType(ID);
    assert facetType instanceof JavaGradleFacetType;
    return (JavaGradleFacetType)facetType;
  }

  @Override
  public void initFacet() {
    MessageBusConnection connection = getModule().getMessageBus().connect(this);
    connection.subscribe(PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!isDisposed()) {
            PsiDocumentManager.getInstance(getModule().getProject()).commitAllDocuments();
            updateConfiguration();
          }
        });
      }
    });
    updateConfiguration();
  }

  private void updateConfiguration() {
    JavaGradleFacetConfiguration config = getConfiguration();
    try {
      saveFacetConfiguration(config);
    }
    catch (WriteExternalException e) {
      LOG.error("Unable to save contents of 'Java-Gradle' facet", e);
    }
  }

  @Nullable
  public String getGradleTaskName(@NotNull BuildMode buildMode) {
    if (!getConfiguration().BUILDABLE) {
      return null;
    }
    switch (buildMode) {
      case ASSEMBLE:
        return GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME;
      case COMPILE_JAVA:
        return COMPILE_JAVA_TASK_NAME;
      default:
        return null;
    }
  }

  public void setJavaProject(@NotNull JavaProject javaProject) {
    myJavaProject = javaProject;
  }

  @Nullable
  public JavaProject getJavaProject() {
    return myJavaProject;
  }
}
