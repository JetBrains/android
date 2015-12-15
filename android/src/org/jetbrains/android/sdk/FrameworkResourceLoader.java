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

package org.jetbrains.android.sdk;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.*;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.BufferingFolderWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/** Loader which loads in a {@link com.android.ide.common.resources.FrameworkResources} */
public class FrameworkResourceLoader {
  private static final Logger LOG = Logger.getInstance(FrameworkResourceLoader.class);

  private FrameworkResourceLoader() {
  }

  @Nullable
  public static FrameworkResources load(@NotNull IAndroidTarget myTarget, boolean withLocale) throws IOException {
    final ILogger logger = new LogWrapper(LOG);
    final File resFolder = myTarget.getFile(IAndroidTarget.RESOURCES);
    if (!resFolder.isDirectory()) {
      LOG.error(AndroidBundle.message("android.directory.cannot.be.found.error", resFolder.getPath()));
      return null;
    }

    return loadPlatformResources(resFolder, logger, withLocale);
  }

  private static FrameworkResources loadPlatformResources(File resFolder, ILogger log, boolean withLocale) throws IOException {
    final IAbstractFolder resFolderWrapper = new BufferingFolderWrapper(resFolder);
    final FrameworkResources resources = new IdeFrameworkResources(resFolderWrapper, withLocale);
    resources.ensureInitialized();
    resources.loadPublicResources(log);
    return resources;
  }

  public static class IdeFrameworkResources extends FrameworkResources {
    private boolean myWithLocales;

    public IdeFrameworkResources(@NonNull IAbstractFolder resFolder, boolean withLocale) {
      super(resFolder);
      myWithLocales = withLocale;
    }

    private boolean myCleared = true;
    private boolean myInitializing = false;

    @Override
    public synchronized void clear() {
      super.clear();
      myCleared = true;
    }

    public synchronized boolean getSkippedLocales() {
      return !myWithLocales;
    }

    @Override
    public synchronized boolean ensureInitialized() {
      if (myCleared && !myInitializing) {


        ScanningContext context = new ScanningContext(this);
        myInitializing = true;

        IAbstractResource[] resources = getResFolder().listMembers();

        for (IAbstractResource res : resources) {
          if (res instanceof IAbstractFolder) {
            IAbstractFolder folder = (IAbstractFolder)res;
            String resFolderName = folder.getName();
            if (resFolderName.startsWith("values-mcc") || resFolderName.startsWith("raw-")) {
              continue;
            }

            // Skip locale-specific folders
            if (!myWithLocales && resFolderName.startsWith("values-")) {
              // Can I find out which resources we use in layoutlib?
              // Can I find out which ones we *expose* through public? I should filter JUST those!
              // I guess I could cache this stuff...?
              FolderConfiguration config = FolderConfiguration.getConfigForFolder(resFolderName);
              if (config == null || config.getLocaleQualifier() != null) {
                continue;
              }
            }

            ResourceFolder resFolder = processFolder(folder);
            if (resFolder != null) {
              IAbstractResource[] files = folder.listMembers();
              for (IAbstractResource fileRes : files) {
                if (fileRes instanceof IAbstractFile) {
                  IAbstractFile file = (IAbstractFile)fileRes;
                  resFolder.processFile(file, ResourceDeltaKind.ADDED, context);
                }
              }
            }
          }
        }

        myInitializing = false;
        myCleared = false;
        return true;
      }

      return false;
    }
  }
}
