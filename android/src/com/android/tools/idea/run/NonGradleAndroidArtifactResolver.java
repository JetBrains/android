// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface NonGradleAndroidArtifactResolver {

  static NonGradleAndroidArtifactResolver getInstance(){
    return ApplicationManager.getApplication().getService(NonGradleAndroidArtifactResolver.class);
  }

  @Nullable String getModuleApkPathByArtifactName(@NotNull Module module, @NotNull String artifactName) throws ApkProvisionException;

  class Dummy implements NonGradleAndroidArtifactResolver{

    @Override
    public @Nullable String getModuleApkPathByArtifactName(@NotNull Module module, @NotNull String artifactName) throws ApkProvisionException {
      return null;
    }
  }
}
