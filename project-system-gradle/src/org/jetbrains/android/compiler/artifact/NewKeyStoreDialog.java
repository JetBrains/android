package org.jetbrains.android.compiler.artifact;

import com.intellij.CommonBundle;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import javax.swing.border.TitledBorder;
import org.jetbrains.android.exportSignedPackage.NewKeyForm;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.SaveFileListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class NewKeyStoreDialog extends DialogWrapper {
  private JPanel myNewKeyPanel;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myKeyStorePathField;
  private JPasswordField myPasswordField;
  private JPasswordField myConfirmedPassword;

  private final NewKeyForm myNewKeyForm;
  private final Project myProject;

  public NewKeyStoreDialog(@NotNull Project project, @NotNull String defaultKeyStorePath) {
    super(project);
    setupUI();
    myProject = project;
    myKeyStorePathField.setText(defaultKeyStorePath);
    setTitle("New Key Store");
    myNewKeyForm = new MyNewKeyForm();
    myNewKeyPanel.add(myNewKeyForm.getContentPanel(), BorderLayout.CENTER);

    myKeyStorePathField.addActionListener(new SaveFileListener(myPanel, myKeyStorePathField, AndroidBundle.message(
      "android.extract.package.choose.keystore.title"), "jks") {
      @Override
      protected String getDefaultLocation() {
        return getKeyStorePath();
      }
    });
    init();
  }

  @Override
  protected void init() {
    super.init();

    myPasswordField.setName("myPasswordField");
    myConfirmedPassword.setName("myConfirmedPassword");
    myNewKeyForm.init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myKeyStorePathField;
  }

  @Override
  protected void doOKAction() {
    if (getKeyStorePath().isEmpty()) {
      Messages.showErrorDialog(myPanel, "Specify key store path", CommonBundle.getErrorTitle());
      return;
    }

    try {
      AndroidUtils.checkNewPassword(myPasswordField, myConfirmedPassword);
      myNewKeyForm.createKey();
    }
    catch (CommitStepException e) {
      Messages.showErrorDialog(myPanel, e.getMessage(), CommonBundle.getErrorTitle());
      return;
    }
    super.doOKAction();
  }

  @NotNull
  public String getKeyStorePath() {
    return myKeyStorePathField.getText().trim();
  }

  @NotNull
  public char[] getKeyStorePassword() {
    return myPasswordField.getPassword();
  }

  @NotNull
  public String getKeyAlias() {
    return myNewKeyForm.getKeyAlias();
  }

  @NotNull
  public char[] getKeyPassword() {
    return myNewKeyForm.getKeyPassword();
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Key store path:");
    jBLabel1.setDisplayedMnemonic('K');
    jBLabel1.setDisplayedMnemonicIndex(0);
    myPanel.add(jBLabel1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myKeyStorePathField = new TextFieldWithBrowseButton();
    myPanel.add(myKeyStorePathField, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                         null, 0, false));
    myNewKeyPanel = new JPanel();
    myNewKeyPanel.setLayout(new BorderLayout(0, 0));
    myNewKeyPanel.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
    myPanel.add(myNewKeyPanel, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    myNewKeyPanel.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "Key",
                                                                                     TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                     TitledBorder.DEFAULT_POSITION, null, null));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Password:");
    jBLabel2.setDisplayedMnemonic('P');
    jBLabel2.setDisplayedMnemonicIndex(0);
    myPanel.add(jBLabel2,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPasswordField = new JPasswordField();
    myPanel.add(myPasswordField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(150, -1), null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Confirm:");
    label1.setDisplayedMnemonic('N');
    label1.setDisplayedMnemonicIndex(2);
    myPanel.add(label1,
                new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myConfirmedPassword = new JPasswordField();
    myPanel.add(myConfirmedPassword, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                         new Dimension(150, -1), null, 0, false));
    jBLabel1.setLabelFor(myKeyStorePathField);
    jBLabel2.setLabelFor(myPasswordField);
    label1.setLabelFor(myConfirmedPassword);
  }

  private class MyNewKeyForm extends NewKeyForm {

    @Override
    protected List<String> getExistingKeyAliasList() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    protected Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    protected char[] getKeyStorePassword() {
      return NewKeyStoreDialog.this.getKeyStorePassword();
    }

    @NotNull
    @Override
    protected String getKeyStoreLocation() {
      return getKeyStorePath();
    }
  }
}
