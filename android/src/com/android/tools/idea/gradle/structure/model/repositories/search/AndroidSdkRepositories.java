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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.sdk.IdeSdks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.ide.common.repository.SdkMavenRepository.ANDROID;
import static com.android.ide.common.repository.SdkMavenRepository.GOOGLE;

public final class AndroidSdkRepositories {
  private AndroidSdkRepositories() {
  }

  @Nullable
  public static ArtifactRepository getAndroidRepository() {
    File location = getRepositoryLocation(ANDROID);
    return location != null ? new LocalMavenRepository(location, "Android Repository") : null;
  }

  @Nullable
  public static ArtifactRepository getGoogleRepository() {
    File location = getRepositoryLocation(GOOGLE);
    return location != null ? new LocalMavenRepository(location, "Google Repository") : null;
  }

  @Nullable
  private static File getRepositoryLocation(@NotNull SdkMavenRepository repository) {
    File androidSdkPath = IdeSdks.getAndroidSdkPath();
    return repository.getRepositoryLocation(androidSdkPath, true, FileOpUtils.create());
  }
}
