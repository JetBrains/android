package org.jetbrains.android.compiler.artifact;

import com.intellij.CommonBundle;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.exportSignedPackage.NewKeyForm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class ChooseKeyDialog extends DialogWrapper {
  private JPanel myNewKeyPanel;
  private JBRadioButton myCreateNewKeyRadioButton;
  private JBRadioButton myUseExistingKeyRadioButton;
  private JComboBox myKeyCombo;
  private JPanel myPanel;

  private final NewKeyForm myNewKeyForm = new MyNewKeyForm();
  private final Project myProject;
  private final String myKeyStorePath;
  private final char[] myKeyStorePassword;
  private final List<String> myExistingKeys;

  public ChooseKeyDialog(@NotNull Project project,
                         @NotNull String keyStorePath,
                         @NotNull char[] password,
                         @NotNull List<String> existingKeys,
                         @Nullable String keyToSelect) {
    super(project);
    setupUI();
    myProject = project;
    myKeyStorePath = keyStorePath;
    myKeyStorePassword = password;
    myExistingKeys = existingKeys;
    myKeyCombo.setModel(new CollectionComboBoxModel(existingKeys, existingKeys.get(0)));

    if (keyToSelect != null && existingKeys.contains(keyToSelect)) {
      myKeyCombo.setSelectedItem(keyToSelect);
    }
    myNewKeyPanel.add(myNewKeyForm.getContentPanel(), BorderLayout.CENTER);

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myNewKeyPanel, myCreateNewKeyRadioButton.isSelected(), true);
      }
    };
    myCreateNewKeyRadioButton.addActionListener(listener);
    myUseExistingKeyRadioButton.addActionListener(listener);

    final boolean useExisting = !existingKeys.isEmpty();
    myUseExistingKeyRadioButton.setSelected(useExisting);
    myCreateNewKeyRadioButton.setSelected(!useExisting);
    UIUtil.setEnabled(myNewKeyPanel, !useExisting, true);

    setTitle("Choose Key");
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCreateNewKeyRadioButton.isSelected()) {
      try {
        myNewKeyForm.createKey();
      }
      catch (CommitStepException e) {
        Messages.showErrorDialog(myPanel, e.getMessage(), CommonBundle.getErrorTitle());
        return;
      }
    }
    super.doOKAction();
  }

  @Nullable
  public String getChosenKey() {
    return myUseExistingKeyRadioButton.isSelected()
           ? (String)myKeyCombo.getSelectedItem()
           : myNewKeyForm.getKeyAlias();
  }

  @Nullable
  public char[] getChosenKeyPassword() {
    return myCreateNewKeyRadioButton.isSelected()
           ? myNewKeyForm.getKeyPassword()
           : null;
  }

  public boolean isNewKeyCreated() {
    return myCreateNewKeyRadioButton.isSelected();
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    myUseExistingKeyRadioButton = new JBRadioButton();
    myUseExistingKeyRadioButton.setText("Use an existing key:");
    myUseExistingKeyRadioButton.setMnemonic('U');
    myUseExistingKeyRadioButton.setDisplayedMnemonicIndex(0);
    myPanel.add(myUseExistingKeyRadioButton,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myCreateNewKeyRadioButton = new JBRadioButton();
    myCreateNewKeyRadioButton.setText("Create a new key");
    myCreateNewKeyRadioButton.setMnemonic('N');
    myCreateNewKeyRadioButton.setDisplayedMnemonicIndex(9);
    myPanel.add(myCreateNewKeyRadioButton,
                new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNewKeyPanel = new JPanel();
    myNewKeyPanel.setLayout(new BorderLayout(0, 0));
    myPanel.add(myNewKeyPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 2, false));
    myKeyCombo = new JComboBox();
    myPanel.add(myKeyCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myUseExistingKeyRadioButton);
    buttonGroup.add(myCreateNewKeyRadioButton);
  }

  private class MyNewKeyForm extends NewKeyForm {
    @Override
    protected List<String> getExistingKeyAliasList() {
      return myExistingKeys;
    }

    @NotNull
    @Override
    protected Project getProject() {
      return myProject;
    }

    @NotNull
    @Override
    protected char[] getKeyStorePassword() {
      return myKeyStorePassword;
    }

    @NotNull
    @Override
    protected String getKeyStoreLocation() {
      return myKeyStorePath;
    }
  }
}
