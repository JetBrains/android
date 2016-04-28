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
package com.android.tools.idea.templates;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.SetMultimap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import freemarker.template.*;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Method invoked by FreeMarker to check whether a given dependency is available in this module.
 *
 * <p>Arguments:
 * <ol>
 *   <li>Maven group and artifact IDs, e.g. {@code "com.android.support:appcompat-v7"}
 *   <li>(Optional) Name of the Gradle configuration to check. Defaults to {@code "compile"}.
 * </ol>
 * <p>Example usage: {@code espresso=hasDependency('com.android.support.test.espresso:espresso-core', 'androidTestCompile')}.
 */
public class FmHasDependencyMethod implements TemplateMethodModelEx {
  private final Map<String, Object> myParamMap;

  public FmHasDependencyMethod(Map<String, Object> paramMap) {
    myParamMap = paramMap;
  }

  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() < 1 || args.size() > 2) {
      throw new TemplateModelException("Wrong arguments");
    }
    String artifact = ((TemplateScalarModel)args.get(0)).getAsString();
    if (artifact.isEmpty()) {
      return TemplateBooleanModel.FALSE;
    }

    // Determine the configuration to check, based on the second argument passed to the function. Defaults to "compile".
    String configuration = SdkConstants.GRADLE_COMPILE_CONFIGURATION;
    if (args.size() > 1) {
      configuration = ((TemplateScalarModel)args.get(1)).getAsString();
    }

    if (myParamMap.containsKey(TemplateMetadata.ATTR_DEPENDENCIES_MULTIMAP)) {
      Object untyped = myParamMap.get(TemplateMetadata.ATTR_DEPENDENCIES_MULTIMAP);
      if (untyped instanceof SetMultimap) {
        @SuppressWarnings("unchecked") SetMultimap<String, String> dependencies = (SetMultimap<String, String>)untyped;
        for (String dependency : dependencies.get(configuration)) {
          if (dependency.contains(artifact)) {
            return TemplateBooleanModel.TRUE;
          }
        }
      }
    }

    // Find the corresponding module, if any
    String modulePath = (String)myParamMap.get(TemplateMetadata.ATTR_PROJECT_OUT);
    if (modulePath != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(modulePath.replace('/', File.separatorChar)));
      if (file != null) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(file);
        if (project != null) {
          Module module = ModuleUtilCore.findModuleForFile(file, project);
          if (module != null) {
            AndroidFacet facet = AndroidFacet.getInstance(module);
            if (facet != null) {
              // TODO: b/23032990
              AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
              if (androidModel != null) {
                boolean dependsOn;
                switch (configuration) {
                  case SdkConstants.GRADLE_COMPILE_CONFIGURATION:
                    dependsOn = GradleUtil.dependsOn(androidModel, artifact);
                    break;
                  case SdkConstants.GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION:
                    dependsOn = GradleUtil.dependsOnAndroidTest(androidModel, artifact);
                    break;
                  default:
                    throw new TemplateModelException("Unknown dependency configuration " + configuration);
                }

                return dependsOn ? TemplateBooleanModel.TRUE : TemplateBooleanModel.FALSE;
              }
            }
          }
        }
      }
    }

    // Creating a new module, so no existing dependencies: provide some defaults. This is really intended for appcompat-v7,
    // but since it depends on support-v4, we include it here (such that a query to see if support-v4 is installed in a newly
    // created project will return true since it will be by virtue of appcompat also being installed.)
    if (artifact.contains(SdkConstants.APPCOMPAT_LIB_ARTIFACT) || artifact.contains(SdkConstants.SUPPORT_LIB_ARTIFACT)) {
      // No dependencies: Base it off of the minApi and buildApi versions:
      // If building with Lollipop, and targeting anything earlier than Lollipop, use appcompat.
      // (Also use it if minApi is less than ICS.)
      Object buildApiObject = myParamMap.get(TemplateMetadata.ATTR_BUILD_API);
      Object minApiObject = myParamMap.get(TemplateMetadata.ATTR_MIN_API_LEVEL);
      if (buildApiObject instanceof Integer && minApiObject instanceof Integer) {
        int buildApi = (Integer)buildApiObject;
        int minApi = (Integer)minApiObject;
        return minApi >= 8 && ((buildApi >= 21 && minApi < 21) || minApi < 14) ? TemplateBooleanModel.TRUE : TemplateBooleanModel.FALSE;
      }
    }

    return TemplateBooleanModel.FALSE;
  }
}