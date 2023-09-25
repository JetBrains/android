/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CustomSkinDefinitionResolverTest {
  private final @NotNull FileSystem myFileSystem = Jimfs.newFileSystem(Configuration.unix());

  private final @NotNull Path PIXEL_4 = myFileSystem.getPath("/home/user/Android/Sdk/skins/pixel_4");

  @NotNull
  private final Path NO_SKIN = SkinUtils.noSkin(myFileSystem);

  @Test
  public void customSkinDefinitionResolverDeviceFrameIsEnabledCustomSkinDefinitionBackupIsNull() {
    // Act
    CustomSkinDefinitionResolver resolver = new CustomSkinDefinitionResolver(myFileSystem, true, PIXEL_4, null);

    // Assert
    assertEquals(Optional.of(PIXEL_4), resolver.getCustomSkinDefinition());
    assertEquals(Optional.empty(), resolver.getCustomSkinDefinitionBackup());
  }

  @Test
  public void customSkinDefinitionResolverDeviceFrameIsntEnabledCustomSkinDefinitionBackupIsNull() {
    // Act
    CustomSkinDefinitionResolver resolver = new CustomSkinDefinitionResolver(myFileSystem, false, PIXEL_4, null);

    // Assert
    assertEquals(Optional.of(NO_SKIN), resolver.getCustomSkinDefinition());
    assertEquals(Optional.of(PIXEL_4), resolver.getCustomSkinDefinitionBackup());
  }

  @Test
  public void customSkinDefinitionResolverDeviceFrameIsEnabledCustomSkinDefinitionBackupIsntNull() {
    // Act
    CustomSkinDefinitionResolver resolver = new CustomSkinDefinitionResolver(myFileSystem, true, NO_SKIN, PIXEL_4);

    // Assert
    assertEquals(Optional.of(PIXEL_4), resolver.getCustomSkinDefinition());
    assertEquals(Optional.empty(), resolver.getCustomSkinDefinitionBackup());
  }
}
