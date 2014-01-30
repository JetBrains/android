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
package com.android.tools.idea.model;

import com.android.SdkConstants;
import com.google.common.base.Charsets;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class ManifestFile {
  private final Module myModule;
  private final VirtualFile myVFile;
  private final File myIoFile;
  private final boolean myIsMerged;

  private XmlFile myXmlFile;
  private long myLastModified = 0;

  private ManifestFile(@NotNull Module module, @NotNull VirtualFile file, boolean isMergedManifest) {
    myModule = module;
    myVFile = file;
    myIoFile = isMergedManifest ? VfsUtilCore.virtualToIoFile(file) : null;
    myIsMerged = isMergedManifest;
  }

  public static ManifestFile create(@NotNull Module module, boolean preferMergedManifest) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    VirtualFile manifestFile = null;
    boolean usingMergedManifest = true;
    if (preferMergedManifest) {
      manifestFile = AndroidRootUtil.getMergedManifestFile(facet);
    }
    if (manifestFile == null) {
      manifestFile = AndroidRootUtil.getManifestFile(facet);
      usingMergedManifest = false;
    }

    if (manifestFile == null) {
      return null;
    }

    return new ManifestFile(module, manifestFile, usingMergedManifest);
  }

  @Nullable
  private XmlFile parseManifest() {
    PsiFile psiFile = PsiManager.getInstance(myModule.getProject()).findFile(myVFile);
    if (psiFile instanceof XmlFile) {
      return (XmlFile)psiFile;
    }

    // merged manifest is present inside the build folder which is excluded
    // so we have to manually read its contents and create a PSI file out of its contents
    try {
      myVFile.setCharset(Charsets.UTF_8);
      String contents = VfsUtilCore.loadText(myVFile);
      psiFile = PsiFileFactory.getInstance(myModule.getProject())
        .createFileFromText(SdkConstants.FN_ANDROID_MANIFEST_XML, XmlFileType.INSTANCE, contents);
      return psiFile instanceof XmlFile ? (XmlFile)psiFile : null;
    }
    catch (IOException e) {
      return null;
    }
  }

  public boolean refresh() {
    long lastModified = getLastModified();
    if (myXmlFile == null || myLastModified < lastModified) {
      myXmlFile = parseManifest();
      myLastModified = lastModified;
      return true;
    } else {
      return false;
    }
  }

  private long getLastModified() {
    if (myIsMerged) {
      return myIoFile.lastModified();
    } else if (myXmlFile != null) {
      return myXmlFile.getModificationStamp();
    } else {
      return 0;
    }
  }

  public XmlFile getXmlFile() {
    return myXmlFile;
  }
}
