/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.android.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.idea.res.IdeResourcesUtil;

public class AndroidProblemFileHighlightingFilter implements Condition<VirtualFile> {
  private final Project myProject;

  public AndroidProblemFileHighlightingFilter(Project project) {
    myProject = project;
  }

  @Override
  public boolean value(VirtualFile file) {
    if (SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      Module module = ModuleUtilCore.findModuleForFile(file, myProject);
      return module != null && AndroidFacet.getInstance(module) != null;
    }

    VirtualFile parent = file.getParent();
    if (parent == null) return false;
    parent = parent.getParent();
    if (parent == null) return false;
    return IdeResourcesUtil.isLocalResourceDirectory(parent, myProject);
  }
}
