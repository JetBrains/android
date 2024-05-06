/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editors.manifest;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.projectmodel.ResourceFolder;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

public class ManifestPanelGradleTokenTest {

  @Test
  public void testGetDependencyDisplayName() {
    ManifestPanelToken<GradleProjectSystem> token = new ManifestPanelGradleToken();

    assertThat(token.getExternalAndroidLibraryDisplayName(new FakeExternalAndroidLibrary("com.google.guava:guava:11.0.2")))
      .isEqualTo("guava:11.0.2");
    assertThat(token.getExternalAndroidLibraryDisplayName(
      new FakeExternalAndroidLibrary("android.arch.lifecycle:extensions:1.0.0-beta1@aar")))
      .isEqualTo("lifecycle:extensions:1.0.0-beta1");
    assertThat(token.getExternalAndroidLibraryDisplayName(
      new FakeExternalAndroidLibrary("com.android.support.test.espresso:espresso-core:3.0.1@aar")))
      .isEqualTo("espresso-core:3.0.1");
    assertThat(token.getExternalAndroidLibraryDisplayName(new FakeExternalAndroidLibrary("foo:bar:1.0"))).isEqualTo("foo:bar:1.0");
  }

  private static class FakeExternalAndroidLibrary implements ExternalAndroidLibrary {
    @NotNull String address;
    FakeExternalAndroidLibrary(@NotNull String address) {
      this.address = address;
    }

    @NotNull
    @Override
    public String getAddress() {
      return address;
    }

    @Nullable
    @Override
    public PathString getLocation() {
      return null;
    }

    @Nullable
    @Override
    public PathString getManifestFile() {
      return null;
    }

    @Nullable
    @Override
    public String getPackageName() {
      return null;
    }

    @Nullable
    @Override
    public ResourceFolder getResFolder() {
      return null;
    }

    @Nullable
    @Override
    public PathString getAssetsFolder() {
      return null;
    }

    @Nullable
    @Override
    public PathString getSymbolFile() {
      return null;
    }

    @Nullable
    @Override
    public PathString getResApkFile() {
      return null;
    }

    @Override
    public boolean getHasResources() {
      return false;
    }
  }
}