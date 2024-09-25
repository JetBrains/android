/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.resources;

import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_VALUES;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import javax.annotation.Nullable;
import org.jetbrains.android.dom.resources.ResourcesDomFileDescription;

/**
 * Supplements {@link ResourcesDomFileDescription} for external resources with odd locations for the
 * AndroidManifest.xml, since blaze projects have no restrictions on the location and name of
 * AndroidManifest.xml files.
 */
public class BlazeResourcesDomFileDescription extends ResourcesDomFileDescription {
  @Override
  public boolean isMyFile(XmlFile file, @Nullable Module module) {
    return !super.isMyFile(file, module) && isBlazeResourcesFile(file);
  }

  /** Only check that the file is under res/values or res/values-*. */
  private static boolean isBlazeResourcesFile(PsiFile file) {
    if (!Blaze.isBlazeProject(file.getProject())) {
      return false;
    }
    file = file.getOriginalFile();
    PsiDirectory parent = file.getContainingDirectory();
    if (parent == null) {
      return false;
    }
    String parentName = parent.getName();
    if (!parentName.equals(FD_RES_VALUES) && !parentName.startsWith(FD_RES_VALUES + '-')) {
      return false;
    }
    PsiDirectory grandParent = parent.getParentDirectory();
    return grandParent != null && grandParent.getName().equals(FD_RES);
  }
}
