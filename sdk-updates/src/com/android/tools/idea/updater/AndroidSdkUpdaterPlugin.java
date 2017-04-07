/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.updater;

import com.android.repository.api.ConstantSourceProvider;
import com.android.repository.api.RepositorySourceProvider;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.ide.externalComponents.ExternalComponentManager;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdkManagerEnabled;

/**
 * Plugin to set up the android sdk {@link UpdatableExternalComponent} and
 * {@link com.android.tools.idea.updater.configure.SdkUpdaterConfigurable}.
 */
public class AndroidSdkUpdaterPlugin implements ApplicationComponent {
  @Override
  public void initComponent() {
    if (isAndroidSdkManagerEnabled()) {
      ExternalComponentManager.getInstance().registerComponentSource(new SdkComponentSource());

      URL offlineRepo = getOfflineRepoDir();
      if (offlineRepo != null) {
        // We don't have an actual RepoManager yet, so just get all the modules statically.
        RepositorySourceProvider provider =
          new ConstantSourceProvider(offlineRepo.toString(), "Offline Repo", AndroidSdkHandler.getAllModules());
        AndroidSdkHandler.addCustomSourceProvider(provider, new StudioLoggerProgressIndicator(getClass()));
      }
    }
  }

  @Nullable
  private static URL getOfflineRepoDir() {
    Path path = Paths.get(PathManager.getPreInstalledPluginsPath(), "sdk-updates", "offline-repo", "offline-repo.xml");
    if (Files.exists(path)) {
      try {
        return path.toUri().toURL();
      }
      catch (MalformedURLException e) {
        return null;
      }
    }
    return null;
  }

  @Override
  public void disposeComponent() {
    // nothing
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Android Sdk Updater";
  }
}
