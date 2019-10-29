// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.SdkConstants;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProblemFileHighlightingFilter implements Condition<VirtualFile> {
  private final Project myProject;

  public AndroidProblemFileHighlightingFilter(Project project) {
    myProject = project;
  }

  @Override
  public boolean value(VirtualFile file) {
    if (file.getFileType() != StdFileTypes.XML) {
      return false;
    }
    if (SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      Module module = ModuleUtilCore.findModuleForFile(file, myProject);
      return module != null && AndroidFacet.getInstance(module) != null;
    }

    VirtualFile parent = file.getParent();
    if (parent == null) return false;
    parent = parent.getParent();
    if (parent == null) return false;
    return AndroidResourceUtil.isLocalResourceDirectory(parent, myProject);
  }
}
