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
import com.intellij.CommonBundle;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.PlatformIcons;
import java.awt.Dimension;
import java.awt.Insets;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.dom.manifest.Action;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class NewAndroidComponentDialog extends DialogWrapper {
  private JPanel myPanel;
  private JLabel myKindLabel;
  private JTextField myNameField;
  private JLabel myUpDownHint;
  private TemplateKindCombo myKindCombo;
  private JTextField myLabelField;
  private JCheckBox myMarkAsStartupActivityCheckBox;
  private JBCheckBox myCreateLayoutFile;

  private ElementCreator myCreator;

  private PsiElement[] myCreatedElements;

  public NewAndroidComponentDialog(@NotNull final Module module, @NotNull PsiDirectory directory) {
    super(module.getProject());
    setupUI();
    myKindLabel.setLabelFor(myKindCombo);
    myKindCombo.registerUpDownHint(myNameField);
    myUpDownHint.setIcon(PlatformIcons.UP_DOWN_ARROWS);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.activity.item"), null, AndroidFileTemplateProvider.ACTIVITY);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.fragment.item"), null, AndroidFileTemplateProvider.FRAGMENT);

    if (!containsCustomApplicationClass(module)) {
      myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.application.item"), null,
                          AndroidFileTemplateProvider.APPLICATION);
    }

    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.service.item"), null, AndroidFileTemplateProvider.SERVICE);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.broadcast.receiver.item"), null,
                        AndroidFileTemplateProvider.BROADCAST_RECEIVER);
    myKindCombo.addItem(AndroidBundle.message("android.new.component.dialog.broadcast.remote.interface"), null,
                        AndroidFileTemplateProvider.REMOTE_INTERFACE_TEMPLATE);
    init();
    setTitle(AndroidBundle.message("android.new.component.action.command.name"));
    myCreator = new ElementCreator(module.getProject(), CommonBundle.getErrorTitle()) {

      @Override
      protected PsiElement[] create(String newName) throws Exception {
        final PsiElement element = NewAndroidComponentDialog.this.create(newName, directory, module.getProject());
        if (element != null) {
          return new PsiElement[]{element};
        }
        return PsiElement.EMPTY_ARRAY;
      }

      @Override
      protected String getActionName(String newName) {
        return AndroidBundle.message("android.new.component.action.command.name");
      }
    };
    myKindCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String selected = myKindCombo.getSelectedName();
        myMarkAsStartupActivityCheckBox.setEnabled(AndroidFileTemplateProvider.ACTIVITY.equals(selected));
        myCreateLayoutFile.setEnabled(AndroidFileTemplateProvider.ACTIVITY.equals(selected) ||
                                      AndroidFileTemplateProvider.FRAGMENT.equals(selected));
        myLabelField.setEnabled(!AndroidFileTemplateProvider.REMOTE_INTERFACE_TEMPLATE.equals(selected) &&
                                !AndroidFileTemplateProvider.APPLICATION.equals(selected) &&
                                !AndroidFileTemplateProvider.FRAGMENT.equals(selected));
      }
    });
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("Name:");
    label1.setDisplayedMnemonic('N');
    label1.setDisplayedMnemonicIndex(0);
    myPanel.add(label1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myKindLabel = new JLabel();
    myKindLabel.setText("Kind:");
    myKindLabel.setDisplayedMnemonic('K');
    myKindLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(myKindLabel,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameField = new JTextField();
    myNameField.setColumns(30);
    myPanel.add(myNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                 new Dimension(250, -1), null, 0, false));
    myUpDownHint = new JLabel();
    myUpDownHint.setToolTipText("Pressing Up or Down arrows while in editor changes the kind");
    myPanel.add(myUpDownHint,
                new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myKindCombo = new TemplateKindCombo();
    myPanel.add(myKindCombo, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    myMarkAsStartupActivityCheckBox = new JCheckBox();
    myMarkAsStartupActivityCheckBox.setText("Mark as startup Activity");
    myMarkAsStartupActivityCheckBox.setMnemonic('M');
    myMarkAsStartupActivityCheckBox.setDisplayedMnemonicIndex(0);
    myPanel.add(myMarkAsStartupActivityCheckBox, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    myLabelField = new JTextField();
    myPanel.add(myLabelField, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                  new Dimension(150, -1), null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Label:");
    label2.setDisplayedMnemonic('L');
    label2.setDisplayedMnemonicIndex(0);
    myPanel.add(label2,
                new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCreateLayoutFile = new JBCheckBox();
    myCreateLayoutFile.setText("Create layout file");
    myCreateLayoutFile.setMnemonic('Y');
    myCreateLayoutFile.setDisplayedMnemonicIndex(9);
    myPanel.add(myCreateLayoutFile, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
    label1.setLabelFor(myNameField);
    label2.setLabelFor(myLabelField);
  }

  private static boolean containsCustomApplicationClass(@NotNull final Module module) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    PsiClass applicationClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      @Nullable
      public PsiClass compute() {
        return facade.findClass(AndroidUtils.APPLICATION_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));
      }
    });
    return applicationClass != null && ClassInheritorsSearch.search(applicationClass, module.getModuleScope(), true).findFirst() != null;
  }

  @Nullable
  private PsiElement create(String newName, final PsiDirectory directory, Project project) throws Exception {
    return doCreate(myKindCombo.getSelectedName(), directory, project, newName, myLabelField.getText(),
                    myMarkAsStartupActivityCheckBox.isSelected(), myCreateLayoutFile.isSelected());
  }

  @Nullable
  static PsiElement doCreate(String templateName,
                             PsiDirectory directory,
                             Project project,
                             String newName,
                             String label,
                             boolean startupActivity,
                             boolean createLayoutFile) throws Exception {
    final PsiElement element = AndroidFileTemplateProvider.createFromTemplate(templateName, newName, directory);
    if (element == null) {
      return null;
    }
    Module module = ModuleUtil.findModuleForFile(directory.getVirtualFile(), project);
    if (module != null) {
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      if (element instanceof PsiClass) {
        registerComponent(templateName, (PsiClass)element, JavaDirectoryService.getInstance().getPackage(directory), facet,
                          label, startupActivity);

        if ((AndroidFileTemplateProvider.ACTIVITY.equals(templateName) || AndroidFileTemplateProvider.FRAGMENT.equals(templateName)) &&
            createLayoutFile) {
          final boolean isActivity = AndroidFileTemplateProvider.ACTIVITY.equals(templateName);
          final Manifest manifest = Manifest.getMainManifest(facet);
          final String appPackage = manifest != null ? manifest.getPackage().getValue() : null;

          if (appPackage != null && !appPackage.isEmpty()) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                createLayoutFileForActivityOrFragment(facet, (PsiClass)element, appPackage, directory, isActivity);
              }
            });
          }
        }
      }
    }
    return element;
  }

  private static void createLayoutFileForActivityOrFragment(@NotNull final AndroidFacet facet,
                                                            @NotNull PsiClass activityClass,
                                                            @NotNull final String appPackage,
                                                            @NotNull PsiDirectory resDirectory,
                                                            boolean activity) {
    if (facet.isDisposed() || !activityClass.isValid()) {
      return;
    }
    final String className = activityClass.getName();

    if (className == null) {
      return;
    }
    final XmlFile layoutFile = CreateResourceFileAction.createFileResource(facet, ResourceFolderType.LAYOUT, null, null, null, true,
                                                                           "Create Layout For '" + className + "'",
                                                                           resDirectory, null, false);
    final String layoutFileName = layoutFile != null ? layoutFile.getName() : null;

    if (layoutFileName != null) {
      final PsiMethod[] onCreateMethods = activityClass.findMethodsByName(activity ? "onCreate" : "onCreateView", false);

      if (onCreateMethods.length != 1) {
        return;
      }
      final PsiMethod onCreateMethod = onCreateMethods[0];
      final PsiCodeBlock body = onCreateMethod.getBody();

      if (body != null) {
        final String fieldName = IdeResourcesUtil.getRJavaFieldName(FileUtil.getNameWithoutExtension(layoutFileName));
        final String layoutFieldRef = appPackage + ".R.layout." + fieldName;

        if (activity) {
          addSetContentViewStatement(body, layoutFieldRef);
        }
        else {
          addInflateStatement(body, layoutFieldRef);
        }
      }
    }
  }

  private static void addInflateStatement(final PsiCodeBlock body, final String layoutFieldRef) {
    final Project project = body.getProject();
    final PsiStatement[] statements = body.getStatements();

    if (statements.length == 1) {
      final PsiStatement statement = statements[0];
      WriteCommandAction.writeCommandAction(project, body.getContainingFile()).run(() -> {
        final PsiStatement newStatement = PsiElementFactory.getInstance(project).createStatementFromText(
          "return inflater.inflate(" + layoutFieldRef + ", container, false);", body);
        statement.replace(newStatement);

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(body);
        CodeStyleManager.getInstance(project).reformat(body);
      });
    }
  }

  private static void addSetContentViewStatement(final PsiCodeBlock body, final String layoutFieldRef) {
    final Project project = body.getProject();
    final PsiElement lastBodyElement = body.getLastBodyElement();

    if (lastBodyElement != null) {
      WriteCommandAction.writeCommandAction(project, body.getContainingFile()).run(() -> {
        final PsiStatement newStatement = PsiElementFactory.getInstance(project).createStatementFromText(
          "setContentView(" + layoutFieldRef + ");", body);
        body.addAfter(newStatement, lastBodyElement);

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(body);
        CodeStyleManager.getInstance(project).reformat(body);
      });
    }
  }

  protected static void registerComponent(String templateName,
                                          PsiClass aClass,
                                          PsiPackage aPackage,
                                          AndroidFacet facet,
                                          String label,
                                          boolean startupActivity) {

    final VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(facet);
    if (manifestFile == null ||
        !ReadonlyStatusHandler.ensureFilesWritable(facet.getModule().getProject(), manifestFile)) {
      return;
    }

    final Manifest manifest = AndroidUtils.loadDomElement(facet.getModule(), manifestFile, Manifest.class);
    if (manifest == null) {
      return;
    }

    String packageName = manifest.getPackage().getValue();
    if (packageName == null || packageName.isEmpty()) {
      manifest.getPackage().setValue(aPackage.getQualifiedName());
    }
    Application application = manifest.getApplication();
    if (application == null) return;
    ApplicationComponent component = addToManifest(templateName, aClass, application, startupActivity);
    if (component != null && !label.isEmpty()) {
      component.getLabel().setValue(ResourceValue.literal(label));
    }
  }

  @Nullable
  protected static ApplicationComponent addToManifest(String templateName,
                                                      @NotNull PsiClass aClass,
                                                      @NotNull Application application,
                                                      boolean startupActivity) {
    if (AndroidFileTemplateProvider.ACTIVITY.equals(templateName)) {
      Activity activity = application.addActivity();
      activity.getActivityClass().setValue(aClass);

      if (startupActivity) {
        IntentFilter filter = activity.addIntentFilter();
        Action action = filter.addAction();
        action.getName().setValue(AndroidUtils.LAUNCH_ACTION_NAME);
        Category category = filter.addCategory();
        category.getName().setValue(AndroidUtils.LAUNCH_CATEGORY_NAME);
      }
      return activity;
    }
    else if (AndroidFileTemplateProvider.SERVICE.equals(templateName)) {
      Service service = application.addService();
      service.getServiceClass().setValue(aClass);
      return service;
    }
    else if (AndroidFileTemplateProvider.BROADCAST_RECEIVER.equals(templateName)) {
      Receiver receiver = application.addReceiver();
      receiver.getReceiverClass().setValue(aClass);
      return receiver;
    }
    else if (AndroidFileTemplateProvider.APPLICATION.equals(templateName)) {
      application.getName().setValue(aClass);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    myCreatedElements = myCreator.tryCreate(myNameField.getText());
    if (myCreatedElements.length == 0) {
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected String getHelpId() {
    return "reference.new.android.component";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public PsiElement[] getCreatedElements() {
    return myCreatedElements;
  }
}
