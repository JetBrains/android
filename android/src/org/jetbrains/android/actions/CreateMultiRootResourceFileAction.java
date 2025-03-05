// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.actions;

import com.android.tools.idea.navigator.AndroidProjectView;
import com.google.common.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.android.dom.layout.AndroidLayoutUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.List;

/**
 * Like CreateTypedResourceFileAction but prompts for a root tag
 */
public class CreateMultiRootResourceFileAction extends CreateTypedResourceFileAction {
  private String myLastRootComponentName;

  public CreateMultiRootResourceFileAction(@NotNull String resourcePresentableName,
                                           @NotNull ResourceFolderType resourceFolderType) {
    super(resourcePresentableName, resourceFolderType, false, false);
  }

  @NotNull
  @Override
  protected PsiElement[] invokeDialog(@NotNull Project project, @NotNull DataContext dataContext) {
    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view != null) {
      // If you're in the Android View, we want to ask you not just the filename but also let you
      // create other resource folder configurations
      AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
      if (pane.getId().equals(AndroidProjectView.ID)) {
          return CreateResourceFileAction.getInstance().invokeDialog(project, dataContext);
      }

      final PsiDirectory directory = view.getOrChooseDirectory();
      if (directory != null) {
        InputValidator validator = createValidator(project, directory);
        final AndroidFacet facet = AndroidFacet.getInstance(directory);
        if (facet != null) {
          final MyDialog dialog = new MyDialog(facet, validator);
          dialog.show();
          return PsiElement.EMPTY_ARRAY;
        }
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  protected PsiElement @NotNull [] create(@NotNull String newName, @NotNull PsiDirectory directory) {
    Module module = ModuleUtilCore.findModuleForPsiElement(directory);
    final String rootTag = myLastRootComponentName != null ? myLastRootComponentName : getDefaultRootTag(module);
    return doCreateAndNavigate(newName, directory, rootTag, false, true);
  }

  @NotNull
  @Override
  public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
    assert getResourceFolderType() == ResourceFolderType.LAYOUT; // if not, must override getAllowedTagNames
    return getPossibleRoots(facet);
  }

  @VisibleForTesting
  List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    return AndroidLayoutUtil.getPossibleRoots(facet);
  }

  public class MyDialog extends DialogWrapper {
    private final InputValidator myValidator;

    private JTextField myFileNameField;
    private TextFieldWithAutoCompletion<String> myRootElementField;
    private JPanel myPanel;
    private JPanel myRootElementFieldWrapper;
    private JBLabel myRootElementLabel;

    protected MyDialog(@NotNull AndroidFacet facet, @Nullable InputValidator validator) {
      super(facet.getModule().getProject());
      setupUI();
      myValidator = validator;
      setTitle(AndroidBundle.message("new.typed.resource.dialog.title", resourcePresentableName));
      final List<String> tagNames = getSortedAllowedTagNames(facet);
      myRootElementField = new TextFieldWithAutoCompletion<String>(
        facet.getModule().getProject(), new TextFieldWithAutoCompletion.StringsCompletionProvider(tagNames, null), true, null);
      myRootElementField.setText(getDefaultRootTag(facet.getModule()));
      myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
      myRootElementLabel.setLabelFor(myRootElementField);
      init();

      myFileNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(@NotNull DocumentEvent event) {
          final String text = myFileNameField.getText().trim();
          if (myValidator instanceof InputValidatorEx) {
            setErrorText(((InputValidatorEx)myValidator).getErrorText(text));
          }
        }
      });
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myFileNameField;
    }

    @Override
    protected void doOKAction() {
      final String fileName = myFileNameField.getText().trim();
      myLastRootComponentName = myRootElementField.getText().trim();

      if (fileName.isEmpty()) {
        Messages.showErrorDialog(myPanel, AndroidBundle.message("file.name.not.specified.error"), CommonBundle.getErrorTitle());
        return;
      }
      if (myLastRootComponentName.isEmpty()) {
        Messages.showErrorDialog(myPanel, AndroidBundle.message("root.element.not.specified.error"), CommonBundle.getErrorTitle());
        return;
      }
      if (myValidator == null ||
          myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
        super.doOKAction();
      }
    }

    private void setupUI() {
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
      final JBLabel jBLabel1 = new JBLabel();
      jBLabel1.setText("File name:");
      jBLabel1.setDisplayedMnemonic('F');
      jBLabel1.setDisplayedMnemonicIndex(0);
      myPanel.add(jBLabel1,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final Spacer spacer1 = new Spacer();
      myPanel.add(spacer1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      myFileNameField = new JTextField();
      myPanel.add(myFileNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                       new Dimension(150, -1), null, 0, false));
      myRootElementLabel = new JBLabel();
      myRootElementLabel.setText("Root element:");
      myRootElementLabel.setDisplayedMnemonic('R');
      myRootElementLabel.setDisplayedMnemonicIndex(0);
      myPanel.add(myRootElementLabel,
                  new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      myRootElementFieldWrapper = new JPanel();
      myRootElementFieldWrapper.setLayout(new BorderLayout(0, 0));
      myPanel.add(myRootElementFieldWrapper, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      jBLabel1.setLabelFor(myFileNameField);
    }
  }
}
