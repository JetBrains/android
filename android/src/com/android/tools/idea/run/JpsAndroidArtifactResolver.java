// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import org.jetbrains.android.compiler.artifact.AndroidArtifactUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JpsAndroidArtifactResolver implements NonGradleAndroidArtifactResolver{

  @Override
  public @Nullable String getModuleApkPathByArtifactName(@NotNull Module module, @NotNull String artifactName) throws ApkProvisionException {
    final Artifact artifact = ArtifactManager.getInstance(module.getProject()).findArtifact(artifactName);

    if (artifact == null) {
      throw new ApkProvisionException("ERROR: cannot find artifact \"" + artifactName + '"');
    }
    if (!AndroidArtifactUtil.isRelatedArtifact(artifact, module)) {
      throw new ApkProvisionException("ERROR: artifact \"" +
                                      artifactName +
                                      "\" doesn't contain packaged module \"" +
                                      module.getName() +
                                      '"');
    }
    final String artifactOutPath = artifact.getOutputFilePath();

    if (artifactOutPath == null || artifactOutPath.isEmpty()) {
      throw new ApkProvisionException("ERROR: output path is not specified for artifact \"" + artifactName + '"');
    }
    return FileUtil.toSystemDependentName(artifactOutPath);
  }
}
