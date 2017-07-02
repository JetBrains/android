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

package org.jetbrains.android.util;

import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class SaveFileListener implements ActionListener {
  private final JPanel myContentPanel;
  private final TextFieldWithBrowseButton myTextField;
  private final String myDialogTitle;
  private final String myExtension;

  public SaveFileListener(JPanel contentPanel, TextFieldWithBrowseButton textField, String dialogTitle, String extension) {
    myContentPanel = contentPanel;
    myTextField = textField;
    myDialogTitle = dialogTitle;
    myExtension = extension;
  }

  @Nullable
  protected abstract String getDefaultLocation();

  @Override
  public void actionPerformed(ActionEvent e) {
    String path = myTextField.getText().trim();
    if (path.isEmpty()) {
      String defaultLocation = getDefaultLocation();
      path = defaultLocation != null && !defaultLocation.isEmpty()
             ? defaultLocation
             : SystemProperties.getUserHome();
    }
    File file = new File(path);
    if (!file.exists()) {
      path = SystemProperties.getUserHome();
    }
    FileSaverDescriptor descriptor = new FileSaverDescriptor(myDialogTitle, "Save as *." + myExtension, myExtension);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myContentPanel);

    VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(file.exists() ? file : new File(path));
    if (vf == null) {
      vf = VfsUtil.getUserHomeDir();
    }

    VirtualFileWrapper result = saveFileDialog.save(vf, null);


    if (result == null || result.getFile() == null) {
      return;
    }

    myTextField.setText(result.getFile().getPath());
  }
}
