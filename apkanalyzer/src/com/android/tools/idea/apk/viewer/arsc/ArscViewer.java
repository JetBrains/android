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
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public class ArscViewer implements ApkFileEditorComponent {
  private final ResourceTablePanel myResourceTablePanel;

  @TestOnly
  private final BinaryResourceFile myResourceFile;

  public ArscViewer(byte @NotNull[] arscContent) {
    myResourceFile = new BinaryResourceFile(arscContent);
    myResourceTablePanel = new ResourceTablePanel(myResourceFile);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myResourceTablePanel.getPanel();
  }

  @Override
  public void dispose() {
  }

  @TestOnly
  public BinaryResourceFile getFile() {
    return myResourceFile;
  }
}
