package org.jetbrains.android.refactoring;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AndroidFindStyleApplicationsDialog extends DialogWrapper {
  private JPanel myPanel;
  private JBRadioButton myModuleScopeRadio;
  private JBRadioButton myFileScopeRadio;
  private JBRadioButton myProjectScopeRadio;
  private JBLabel myCaptionLabel;

  private final VirtualFile myFile;
  private final AndroidFindStyleApplicationsProcessor myProcessor;

  private static final String FIND_STYLE_APPLICATIONS_SCOPE_PROPERTY = "ANDROID_FIND_STYLE_APPLICATION_SCOPE";

  protected AndroidFindStyleApplicationsDialog(@Nullable VirtualFile file,
                                               @NotNull AndroidFindStyleApplicationsProcessor processor,
                                               boolean showModuleRadio) {
    super(processor.getModule().getProject(), true);

    setupUI();
    myFile = file;
    myProcessor = processor;

    final Module module = processor.getModule();
    myModuleScopeRadio.setText(CodeInsightBundle.message("scope.option.module.with.mnemonic", module.getName()));
    myModuleScopeRadio.setVisible(showModuleRadio);

    if (file != null) {
      myFileScopeRadio.setText("File '" + file.getName() + "'");
    }
    else {
      myFileScopeRadio.setVisible(false);
    }
    final String scopeValue = PropertiesComponent.getInstance().getValue(FIND_STYLE_APPLICATIONS_SCOPE_PROPERTY);
    AndroidFindStyleApplicationsProcessor.MyScope scope = null;
    if (scopeValue != null) {
      try {
        scope = Enum.valueOf(AndroidFindStyleApplicationsProcessor.MyScope.class, scopeValue);
      }
      catch (IllegalArgumentException e) {
        scope = null;
      }
    }

    if (scope == null) {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.FILE;
    }

    switch (scope) {
      case PROJECT:
        myProjectScopeRadio.setSelected(true);
        break;
      case MODULE:
        myModuleScopeRadio.setSelected(true);
        break;
      case FILE:
        myFileScopeRadio.setSelected(true);
        break;
    }

    if (myModuleScopeRadio.isSelected() && !myModuleScopeRadio.isVisible() ||
        myFileScopeRadio.isSelected() && !myFileScopeRadio.isVisible()) {
      myProjectScopeRadio.setSelected(true);
    }
    myCaptionLabel.setText("Choose a scope where to search possible applications of style '" + myProcessor.getStyleName() + "'");
    setTitle(AndroidBundle.message("android.find.style.applications.title"));
    init();
  }

  @Override
  protected void doOKAction() {
    AndroidFindStyleApplicationsProcessor.MyScope scope;

    if (myModuleScopeRadio.isSelected()) {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.MODULE;
    }
    else if (myProjectScopeRadio.isSelected()) {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.PROJECT;
    }
    else {
      scope = AndroidFindStyleApplicationsProcessor.MyScope.FILE;
    }
    PropertiesComponent.getInstance().setValue(FIND_STYLE_APPLICATIONS_SCOPE_PROPERTY, scope.name());

    myProcessor.configureScope(scope, myFile);
    myProcessor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
      @Override
      public void run() {
        close(DialogWrapper.OK_EXIT_CODE);
      }
    });
    myProcessor.run();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myCaptionLabel = new JBLabel();
    myCaptionLabel.setText("");
    myPanel.add(myCaptionLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                    false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));
    myProjectScopeRadio = new JBRadioButton();
    myProjectScopeRadio.setText("Whole project");
    myProjectScopeRadio.setMnemonic('W');
    myProjectScopeRadio.setDisplayedMnemonicIndex(0);
    panel1.add(myProjectScopeRadio, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myModuleScopeRadio = new JBRadioButton();
    panel1.add(myModuleScopeRadio, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myFileScopeRadio = new JBRadioButton();
    myFileScopeRadio.setText("Current file");
    myFileScopeRadio.setMnemonic('F');
    myFileScopeRadio.setDisplayedMnemonicIndex(8);
    panel1.add(myFileScopeRadio, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myProjectScopeRadio);
    buttonGroup.add(myModuleScopeRadio);
    buttonGroup.add(myFileScopeRadio);
  }
}
