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
package org.jetbrains.android.exportSignedPackage;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import org.jetbrains.android.compiler.artifact.ApkSigningSettingsForm;
import org.jetbrains.android.compiler.artifact.ChooseKeyDialog;
import org.jetbrains.android.compiler.artifact.NewKeyStoreDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExportSignedPackageUtil {
  private static final Logger LOG = Logger.getInstance(ExportSignedPackageUtil.class);

  private ExportSignedPackageUtil() {
  }

  @Nullable
  private static List<String> loadExistingKeys(@NotNull ApkSigningSettingsForm form) {
    final String errorPrefix = "Cannot load key store: ";
    InputStream is = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      is = new FileInputStream(new File(form.getKeyStorePathField().getText().trim()));
      final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(is, form.getKeyStorePasswordField().getPassword());
      return ContainerUtil.toList(keyStore.aliases());
    }
    catch (KeyStoreException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (FileNotFoundException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (CertificateException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (NoSuchAlgorithmException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    catch (IOException e) {
      Messages.showErrorDialog(form.getPanel(), errorPrefix + e.getMessage(), CommonBundle.getErrorTitle());
      return null;
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  public static void initSigningSettingsForm(@NotNull final Project project, @NotNull final ApkSigningSettingsForm form) {
    form.getLoadKeyStoreButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String defaultPath = form.getKeyStorePathField().getText().trim();
        final VirtualFile defaultFile = LocalFileSystem.getInstance().findFileByPath(defaultPath);
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        final VirtualFile file = FileChooser.chooseFile(descriptor, form.getPanel(), project, defaultFile);
        if (file != null) {
          form.getKeyStorePathField().setText(FileUtilRt.toSystemDependentName(file.getPath()));
        }
        form.keyStoreSelected();
      }
    });

    form.getCreateKeyStoreButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final NewKeyStoreDialog dialog = new NewKeyStoreDialog(project, form.getKeyStorePathField().getText());
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          form.getKeyStorePathField().setText(dialog.getKeyStorePath());
          form.getKeyStorePasswordField().setText(String.valueOf(dialog.getKeyStorePassword()));
          form.getKeyAliasField().setText(dialog.getKeyAlias());
          form.getKeyPasswordField().setText(String.valueOf(dialog.getKeyPassword()));
          form.keyStoreCreated();
        }
      }
    });

    form.getKeyAliasField().getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final List<String> keys = loadExistingKeys(form);
        if (keys == null) {
          return;
        }
        final ChooseKeyDialog dialog =
          new ChooseKeyDialog(project, form.getKeyStorePathField().getText().trim(), form.getKeyStorePasswordField().getPassword(), keys,
                              form.getKeyAliasField().getText().trim());
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          final String chosenKey = dialog.getChosenKey();
          if (chosenKey != null) {
            form.getKeyAliasField().setText(chosenKey);
          }

          final char[] password = dialog.getChosenKeyPassword();
          if (password != null) {
            form.getKeyPasswordField().setText(String.valueOf(password));
          }
          if (dialog.isNewKeyCreated()) {
            form.keyAliasCreated();
          }
          else {
            form.keyAliasSelected();
          }
        }
      }
    });
  }
}
