package org.jetbrains.android.compiler.artifact;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public interface ApkSigningSettingsForm {
  JButton getLoadKeyStoreButton();

  JTextField getKeyStorePathField();

  JPanel getPanel();

  JButton getCreateKeyStoreButton();

  JPasswordField getKeyStorePasswordField();

  TextFieldWithBrowseButton getKeyAliasField();

  JPasswordField getKeyPasswordField();

  default void keyStoreSelected() {}
  default void keyStoreCreated() {}
  default void keyAliasSelected() {}
  default void keyAliasCreated() {}
}
