/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Provides Apk whose path is specified when instantiated.
 */
public class FileSystemApkProvider implements ApkProvider {
  private final Module myModule;
  private final File myApkPath;

  public FileSystemApkProvider(@NotNull Module module, @NotNull File apkFile) {
    myModule = module;
    myApkPath = apkFile;
  }

  @NotNull
  @Override
  public Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException {
    String id = ProjectSystemUtil.getModuleSystem(myModule).getPackageName();
    if (id == null) {
      throw new ApkProvisionException("Invalid manifest, no package name specified");
    }

    List<ApkInfo> apkList = new ArrayList<>();
    apkList.add(new ApkInfo(myApkPath, id));
    return apkList;
  }
}