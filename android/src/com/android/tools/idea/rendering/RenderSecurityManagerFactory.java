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
package com.android.tools.idea.rendering;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.sdk.AndroidPlatform;

/**
 * Factory of {@link RenderSecurityManager}.
 */
public class RenderSecurityManagerFactory {
  /**
   * Returns a {@link RenderSecurityManager} for the current module. The {@link RenderSecurityManager} will be
   * setup with the SDK path and project path of the module.
   */
  public static RenderSecurityManager create(Module module, AndroidPlatform platform) {
    String projectPath = null;
    String sdkPath = null;
    if (RenderSecurityManager.RESTRICT_READS) {
      projectPath = module.getProject().getBasePath();
      if (platform != null) {
        sdkPath = platform.getSdkData().getLocation().getPath();
      }
    }

    @SuppressWarnings("ConstantConditions")
    RenderSecurityManager securityManager = new RenderSecurityManager(sdkPath, projectPath);
    securityManager.setLogger(new LogWrapper(RenderLogger.LOG));
    securityManager.setAppTempDir(PathManager.getTempPath());

    return securityManager;
  }
}
