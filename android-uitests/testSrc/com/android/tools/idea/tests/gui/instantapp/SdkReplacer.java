/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.instantapp.InstantAppSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.pico.DefaultPicoContainer;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;

import java.io.File;

import static org.junit.Assert.assertSame;

/**
 * Responsible for replacing {@link com.android.tools.idea.instantapp.InstantAppSdks} for a mock one pointing to a fake SDK location.
 * This is necessary for the moment because Instant App Sdk isn't available in the published SDK yet.
 * Should be deleted one AIA sdk becomes public.
 */
public class SdkReplacer {
  private static InstantAppSdks original = null;

  public static void replaceSdkLocationAndActivate(@Nullable String sdkLocation, boolean activate) {
    if (original == null) {
      original = InstantAppSdks.getInstance();
    }

    replaceService(new InstantAppSdks() {
      @Override
      public File getInstantAppSdk(boolean tryToInstall) {
        if (sdkLocation == null) {
          return null;
        }
        return new File(sdkLocation);
      }

      @Override
      public boolean isInstantAppSdkEnabled() {
        return activate;
      }

      @Override
      public int getMinTargetSdk() {
        return 23;
      }

      @Override
      public long getCompatApiMinVersion() {
        return 1;
      }
    });
  }

  public static void putBack() {
    if (original != null) {
      replaceService(original);
    }
  }

  private static void replaceService(InstantAppSdks newService) {
    DefaultPicoContainer picoContainer = (DefaultPicoContainer)ApplicationManager.getApplication().getPicoContainer();

    String componentKey = InstantAppSdks.class.getName();
    ComponentAdapter componentAdapter = picoContainer.unregisterComponent(componentKey);
    assert componentAdapter != null;

    picoContainer.registerComponentInstance(componentKey, newService);
    assertSame(newService, picoContainer.getComponentInstance(componentKey));
  }
}
