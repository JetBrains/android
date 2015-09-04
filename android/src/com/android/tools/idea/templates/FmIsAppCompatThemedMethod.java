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

import com.android.tools.idea.npw.ThemeHelper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

public class FmIsAppCompatThemedMethod implements TemplateMethodModelEx {
  private final Map<String, Object> myParamMap;

  public FmIsAppCompatThemedMethod(Map<String, Object> paramMap) {
    myParamMap = paramMap;
  }

  @Override
  public Object exec(List arguments) throws TemplateModelException {
    Module module = findModuleIfAny();
    if (module == null) {
      return null;
    }
    ThemeHelper themeHelper = new ThemeHelper(module);
    return themeHelper.hasDefaultAppCompatTheme();
  }

  @Nullable
  private Module findModuleIfAny() {
    String modulePath = (String)myParamMap.get(TemplateMetadata.ATTR_PROJECT_OUT);
    if (modulePath != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(modulePath.replace('/', File.separatorChar)));
      if (file != null) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(file);
        if (project != null) {
          return ModuleUtilCore.findModuleForFile(file, project);
        }
      }
    }
    return null;
  }
}
