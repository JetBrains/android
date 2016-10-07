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
package org.jetbrains.android.dom.manifest;

import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.xml.AndroidManifest;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.NS_RESOURCES;

/**
 * @author yole
 */
public class ManifestDomFileDescription extends DomFileDescription<Manifest> {
  public ManifestDomFileDescription() {
    super(Manifest.class, AndroidManifest.NODE_MANIFEST);
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return (module == null) ? isManifestFile(file) : isManifestFile(file, module);
  }

  public static boolean isManifestFile(@NotNull XmlFile file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    return isManifestFile(file, module);
  }

  public static boolean isManifestFile(@NotNull XmlFile file, @Nullable Module module) {
    if (module != null && !module.isDisposed()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        return isManifestFile(file, facet);
      }
    }

    if (file.getVirtualFile() == null) { // happens while indexing
      return false;
    }

    if (ApkFileSystem.getInstance().equals(file.getVirtualFile().getFileSystem())) {
      return false;
    }

    return file.getName().equals(FN_ANDROID_MANIFEST_XML);
  }

  public static boolean isManifestFile(@NotNull XmlFile file, @NotNull AndroidFacet facet) {
    return file.getName().equals(FN_ANDROID_MANIFEST_XML) ||
           facet.requiresAndroidModel() && IdeaSourceProvider.isManifestFile(facet, file.getVirtualFile());
  }

  @Override
  protected void initializeFileDescription() {
    registerNamespacePolicy(AndroidUtils.NAMESPACE_KEY, NS_RESOURCES);
  }
}
