/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.idea.blaze.android.fixtures;

import com.google.idea.blaze.base.WorkspaceFileSystem;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomManager;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesPermission;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.annotations.Nullable;

/** Fixture for creating and modifying AndroidManifest.xml files in ASwB integration tests. */
public class ManifestFixture {
  private static final String XMLNS = "<?xml version='1.0' encoding='utf-8'?>";
  private static final String EMPTY_MANIFEST =
      XMLNS
          + "<manifest xmlns:android='http://schemas.android.com/apk/res/android'"
          + " package='%s'><application/></manifest>";

  private final Project project;
  public final String label;
  public final XmlFile file;
  private final Manifest manifest;

  private ManifestFixture(Project project, WorkspaceFileSystem workspace, String packageName) {
    this(project, workspace, "java/" + packageName.replace(".", "/"), packageName);
  }

  private ManifestFixture(
      Project project, WorkspaceFileSystem workspace, String relativeDirPath, String packageName) {
    this.project = project;
    String relativePath = relativeDirPath + "/AndroidManifest.xml";
    label = "//" + relativePath;
    file =
        (XmlFile)
            workspace.createPsiFile(
                new WorkspacePath(relativePath), String.format(EMPTY_MANIFEST, packageName));
    manifest =
        DomManager.getDomManager(project).getFileElement(file, Manifest.class).getRootElement();
  }

  public ManifestFixture setMinSdkVersion(int version) {
    return updateManifest(
        manifest -> getOrCreateUsesSdk().getMinSdkVersion().setValue(String.valueOf(version)));
  }

  public ManifestFixture setTargetSdkVersion(int version) {
    return updateManifest(
        manifest -> getOrCreateUsesSdk().getTargetSdkVersion().setValue(String.valueOf(version)));
  }

  private UsesSdk getOrCreateUsesSdk() {
    List<UsesSdk> usesSdks = manifest.getUsesSdks();
    if (usesSdks.isEmpty()) {
      return manifest.addUsesSdk();
    }
    return usesSdks.get(0);
  }

  public ManifestFixture setVersionCode(int versionCode) {
    return updateManifest(manifest -> manifest.getVersionCode().setValue(versionCode));
  }

  public ManifestFixture addUsesPermission(String permissionName) {
    UsesPermission existingPermission = findPermissionNamed(permissionName);
    if (existingPermission != null) {
      return this;
    }

    return updateManifest(
        manifest -> {
          AndroidAttributeValue<String> name = manifest.addUsesPermission().getName();
          if (name == null) {
            return;
          }
          name.setValue(permissionName);
        });
  }

  public ManifestFixture removeUsesPermission(String permissionName) {
    UsesPermission toRemove = findPermissionNamed(permissionName);
    if (toRemove == null) {
      return this;
    }
    return updateManifest(manifest -> toRemove.undefine());
  }

  @Nullable
  private UsesPermission findPermissionNamed(String permissionName) {
    for (UsesPermission permission : manifest.getUsesPermissions()) {
      if (permissionName.equals(permission.getName().getValue())) {
        return permission;
      }
    }
    return null;
  }

  public ManifestFixture updateManifest(Consumer<Manifest> update) {
    ApplicationManager.getApplication()
        .invokeAndWait(
            () -> {
              WriteCommandAction.runWriteCommandAction(project, () -> update.accept(manifest));
              FileDocumentManager.getInstance().saveAllDocuments();
            });
    return this;
  }

  /** Factory to create ManifestFixture */
  public static class Factory {
    private final Project project;
    private final WorkspaceFileSystem workspace;

    public Factory(Project project, WorkspaceFileSystem workspace) {
      this.project = project;
      this.workspace = workspace;
    }

    public ManifestFixture fromPackage(String packageName) {
      return new ManifestFixture(project, workspace, packageName);
    }

    public ManifestFixture inDirectory(String relativeDirPath, String packageName) {
      return new ManifestFixture(project, workspace, relativeDirPath, packageName);
    }
  }
}
