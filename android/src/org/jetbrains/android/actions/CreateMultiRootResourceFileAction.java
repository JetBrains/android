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

package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
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
 * @author Eugene.Kudelevsky
 *
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
      if (pane instanceof AndroidProjectViewPane) {
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
  protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception {
    final String rootTag = myLastRootComponentName != null ? myLastRootComponentName : getDefaultRootTag();
    return doCreateAndNavigate(newName, directory, rootTag, false, true);
  }

  @NotNull
  @Override
  public List<String> getAllowedTagNames(@NotNull AndroidFacet facet) {
    assert myResourceType == ResourceFolderType.LAYOUT; // if not, must override getAllowedTagNames
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
      myValidator = validator;
      setTitle(AndroidBundle.message("new.typed.resource.dialog.title", myResourcePresentableName));
      final List<String> tagNames = getSortedAllowedTagNames(facet);
      myRootElementField = new TextFieldWithAutoCompletion<String>(
        facet.getModule().getProject(), new TextFieldWithAutoCompletion.StringsCompletionProvider(tagNames, null), true, null);
      myRootElementField.setText(myDefaultRootTag);
      myRootElementFieldWrapper.add(myRootElementField, BorderLayout.CENTER);
      myRootElementLabel.setLabelFor(myRootElementField);
      init();

      myFileNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(DocumentEvent event) {
          final String text = myFileNameField.getText().trim();
          if (myValidator instanceof InputValidatorEx) {
            setErrorText(((InputValidatorEx) myValidator).getErrorText(text));
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

      if (fileName.length() == 0) {
        Messages.showErrorDialog(myPanel, AndroidBundle.message("file.name.not.specified.error"), CommonBundle.getErrorTitle());
        return;
      }
      if (myLastRootComponentName.length() == 0) {
        Messages.showErrorDialog(myPanel, AndroidBundle.message("root.element.not.specified.error"), CommonBundle.getErrorTitle());
        return;
      }
      if (myValidator == null ||
          myValidator.checkInput(fileName) && myValidator.canClose(fileName)) {
        super.doOKAction();
      }
    }
  }
}
