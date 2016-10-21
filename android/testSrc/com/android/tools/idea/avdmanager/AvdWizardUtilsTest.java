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

import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.idea.rendering.webp.WebpMetadata;
import com.android.tools.idea.rendering.webp.WebpNativeLibHelper;
import com.google.common.base.Charsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
}