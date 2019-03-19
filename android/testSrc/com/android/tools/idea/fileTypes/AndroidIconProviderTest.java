/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.fileTypes;

import com.android.tools.idea.rendering.FlagManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AndroidIconProviderTest extends AndroidTestCase {
  public void testFlagIcons() throws Exception {
    checkIcon("res/wrong/path.xml", null);
    checkIcon("res/layout/file.xml", null);
    checkIcon("res/layout-land/file.xml", null);
    checkIcon("res/values-no/strings.xml", null);
    checkIcon("res/values-no-rNO/strings.xml", "NO");
    checkIcon("res/values-en-rUS/strings.xml", "US");
    checkIcon("res/values-en-rGB/strings.xml", "GB");
  }

  private void checkIcon(@NotNull String path, @Nullable String region) throws Exception {
    AndroidIconProvider provider = new AndroidIconProvider();
    VirtualFile file = myFixture.getTempDirFixture().createFile(path);
    WriteAction.run(() -> file.setBinaryContent("content does not matter".getBytes()));
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    int flags = 0;
    Icon icon = provider.getIcon(psiFile, flags);
    if (region == null) {
      assertNull(icon);
    } else {
      assertSame(FlagManager.get().getFlag(region), icon);
    }
  }
}
