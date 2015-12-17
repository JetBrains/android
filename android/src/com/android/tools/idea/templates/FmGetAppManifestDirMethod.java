/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

public class FmGetAppManifestDirMethod implements TemplateMethodModelEx {
  private static final String APP_NAME = "app";
  private static final String MOBILE_NAME = "mobile";
  private final Map<String, Object> myParamMap;

  public FmGetAppManifestDirMethod(Map<String, Object> paramMap) {
    myParamMap = paramMap;
  }

  @Override
  public Object exec(List arguments) throws TemplateModelException {
    Module module = findAppModuleIfAny();
    if (module == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    IdeaSourceProvider provider = facet.getMainIdeaSourceProvider();
    VirtualFile file = provider.getManifestFile();
    if (file == null) {
      return null;
    }

    return file.getParent().getCanonicalPath();
  }

  @Nullable
  private Module findAppModuleIfAny() {
    String modulePath = (String)myParamMap.get(TemplateMetadata.ATTR_PROJECT_OUT);
    if (modulePath != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(modulePath.replace('/', File.separatorChar)));
      if (file != null) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(file);
        if (project != null) {
          ModuleManager manager = ModuleManager.getInstance(project);
          Module module = manager.findModuleByName(APP_NAME);
          if (module != null) {
            return module;
          }
          return manager.findModuleByName(MOBILE_NAME);
        }
      }
    }
    return null;
  }
}
