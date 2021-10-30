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

import static com.android.SdkConstants.FD_EMULATOR;
import static com.android.tools.idea.avdmanager.AvdWizardUtils.emulatorSupportsSnapshotManagement;
import static com.android.tools.idea.avdmanager.AvdWizardUtils.emulatorSupportsWebp;
import static com.google.common.truth.Truth.assertThat;

import com.android.repository.Revision;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage.FakeLocalPackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class AvdWizardUtilsTest {
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

    assertThat(emulatorSupportsWebp(createMockSdk("25.2.3", "irrelevant"))).isFalse();
  }

  @Test
  public void testEmulatorSupportsSnapshotManagement() {
    assertThat(emulatorSupportsSnapshotManagement(createMockSdk("27.2.4", FD_EMULATOR))).isFalse();
    assertThat(emulatorSupportsSnapshotManagement(createMockSdk("27.2.5", FD_EMULATOR))).isTrue();
  }

  @NotNull
  private static AndroidSdkHandler createMockSdk(String versionString, String path) {
    FakeLocalPackage p = new FakeLocalPackage(path);
    p.setRevision(Revision.parseRevision(versionString));
    RepositoryPackages packages = new RepositoryPackages();
    packages.setLocalPkgInfos(ImmutableList.of(p));
    RepoManager mgr = new FakeRepoManager(null, packages);
    return new AndroidSdkHandler(null, null, mgr);
  }
}
