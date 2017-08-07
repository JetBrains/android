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
package com.android.tools.idea.avdmanager;

import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.testframework.FakePackage.FakeLocalPackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.adtui.webp.WebpMetadata;
import com.android.tools.adtui.webp.WebpNativeLibHelper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FD_EMULATOR;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.tools.idea.avdmanager.AvdWizardUtils.emulatorSupportsWebp;
import static com.google.common.truth.Truth.assertThat;

public class AvdWizardUtilsTest {
  @Rule
  public TemporaryFolder myFolder = new TemporaryFolder();

  @Test
  public void testResolveSkins() throws IOException {
    WebpMetadata.ensureWebpRegistered();
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Can't run skin conversion test without native webp library");
      return;
    }

    DeviceArtDescriptor pixel = null;
    List<DeviceArtDescriptor> specs = DeviceArtDescriptor.getDescriptors(null);
    for (DeviceArtDescriptor spec : specs) {
      if ("pixel".equals(spec.getId())) {
        pixel = spec;
        break;
      }
    }
    assertThat(pixel).isNotNull();
    File source = pixel.getBaseFolder();

    FileOp fileOp = FileOpUtils.create();
    assertThat(fileOp.exists(new File(source, "port_back.webp"))).isTrue();
    assertThat(fileOp.exists(new File(source, "port_back.png"))).isFalse();
    assertThat(fileOp.toString(new File(source, "layout"), Charsets.UTF_8)).contains(".webp");
    assertThat(fileOp.toString(new File(source, "layout"), Charsets.UTF_8)).doesNotContain(".png");

    File dest = myFolder.getRoot();
    AvdWizardUtils.convertWebpSkinToPng(fileOp, dest, source);

    assertThat(fileOp.exists(new File(dest, "port_back.webp"))).isFalse();
    assertThat(fileOp.exists(new File(dest, "port_back.png"))).isTrue();
    assertThat(fileOp.exists(new File(dest, "layout"))).isTrue();
    assertThat(fileOp.toString(new File(dest, "layout"), Charsets.UTF_8)).contains(".png");
    assertThat(fileOp.toString(new File(dest, "layout"), Charsets.UTF_8)).doesNotContain(".webp");
  }

  @Test
  public void testPngFallthrough() throws IOException {
    // Make sure that we correctly copy png assets
    WebpMetadata.ensureWebpRegistered();
    if (!WebpNativeLibHelper.loadNativeLibraryIfNeeded()) {
      System.out.println("Can't run skin conversion test without native webp library");
      return;
    }

    DeviceArtDescriptor wearSquare = null;
    List<DeviceArtDescriptor> specs = DeviceArtDescriptor.getDescriptors(null);
    for (DeviceArtDescriptor spec : specs) {
      if ("wear_square".equals(spec.getId())) {
        wearSquare = spec;
        break;
      }
    }
    assertThat(wearSquare).isNotNull();
    File source = wearSquare.getBaseFolder();

    FileOp fileOp = FileOpUtils.create();
    assertThat(fileOp.exists(new File(source, "back.png"))).isTrue();
    assertThat(fileOp.exists(new File(source, "back.webp"))).isFalse();
    assertThat(fileOp.toString(new File(source, "layout"), Charsets.UTF_8)).contains(".png");
    assertThat(fileOp.toString(new File(source, "layout"), Charsets.UTF_8)).doesNotContain(".webp");

    File dest = myFolder.getRoot();
    AvdWizardUtils.convertWebpSkinToPng(fileOp, dest, source);

    assertThat(fileOp.exists(new File(dest, "back.webp"))).isFalse();
    assertThat(fileOp.exists(new File(dest, "back.png"))).isTrue();
    assertThat(fileOp.exists(new File(dest, "layout"))).isTrue();
    assertThat(fileOp.toString(new File(dest, "layout"), Charsets.UTF_8)).contains(".png");
    assertThat(fileOp.toString(new File(dest, "layout"), Charsets.UTF_8)).doesNotContain(".webp");
  }

  @Test
  public void testEmulatorSupportsWebp() {
    assertThat(emulatorSupportsWebp(createMockSdk("24.0.0", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.0.0", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.1.0", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.1.9", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.2.0", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.2.2", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.2.3", FD_EMULATOR))).isTrue();
    assertThat(emulatorSupportsWebp(createMockSdk("25.3.0", FD_EMULATOR))).isTrue();
    assertThat(emulatorSupportsWebp(createMockSdk("26.0.0", FD_EMULATOR))).isTrue();

    assertThat(emulatorSupportsWebp(createMockSdk("25.2.0", FD_TOOLS))).isFalse();
    assertThat(emulatorSupportsWebp(createMockSdk("25.2.3", FD_TOOLS))).isTrue();

    assertThat(emulatorSupportsWebp(createMockSdk("25.2.3", "irrelevant"))).isFalse();
  }

  @NotNull
  private static AndroidSdkHandler createMockSdk(String versionString, String path) {
    FakeLocalPackage p = new FakeLocalPackage(path);
    p.setRevision(Revision.parseRevision(versionString));
    RepositoryPackages packages = new RepositoryPackages();
    packages.setLocalPkgInfos(ImmutableList.of(p));
    RepoManager mgr = new FakeRepoManager(null, packages);
    return new AndroidSdkHandler(null, null, new MockFileOp(), mgr);
  }
}
