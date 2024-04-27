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
package com.android.tools.idea.apk.viewer.arsc;

import com.android.tools.idea.apk.viewer.ApkFileEditorComponent;
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ArscViewer implements ApkFileEditorComponent {
  private final ResourceTablePanel myResourceTablePanel;

  public ArscViewer(@NotNull byte[] arscContent) {
    myResourceTablePanel = new ResourceTablePanel(new BinaryResourceFile(arscContent));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myResourceTablePanel.getPanel();
  }

  @Override
  public void dispose() {
  }
}
