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
package com.intellij.androidstudio.actions;

import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class CreateFileFromTemplateDialog extends DialogWrapper {
  private static final String ATTRIBUTE_INTERFACES = "INTERFACES";
  private static final String ATTRIBUTE_VISIBILITY = "VISIBILITY";
  private static final String ATTRIBUTE_SUPERCLASS = "SUPERCLASS";
  private static final String ATTRIBUTE_FINAL = "FINAL";
  private static final String ATTRIBUTE_ABSTRACT = "ABSTRACT";
  private static final String ATTRIBUTE_IMPORT_BLOCK = "IMPORT_BLOCK";

  private JPanel myPanel;
  private JLabel myNameLabel;
  private EditorTextField myNameField;
  private JLabel myKindLabel;
  private TemplateKindCombo myKindCombo;
  private JLabel mySuperclassLabel;
  private JPanel mySuperclassFieldPlaceholder;
  private EditorTextField mySuperclassField;
  private JLabel myInterfacesLabel;
  private JPanel myInterfacesFieldPlaceholder;
  private EditorTextField myInterfacesField;
  private JLabel myPackageLabel;
  private JPanel myPackageFieldPlaceholder;
  private EditorTextField myPackageField;
  private JLabel myVisibilityLabel;
  private JRadioButton myPublicRadioButton;
  private JRadioButton myPackagePrivateRadioButton;
  private JLabel myModifiersLabel;
  private JRadioButton myNoModifierRadioButton;
  private JRadioButton myAbstractRadioButton;
  private JRadioButton myFinalRadioButton;
  private JSeparator myOverridesSeparator;
  private JCheckBox myShowSelectOverridesDialogCheckBox;

  private ElementCreator myCreator;
  private CreateNewClassDialogValidatorEx myInputValidator;

  private final Project myProject;
  private final PsiPackage myDefaultPsiPackage;
  private final JavaCodeFragmentFactory myFragmentFactory;
  private final PsiDocumentManager myPsiDocumentManager;

  private final Map<String, String> myCreationOptions = new HashMap<String, String>();

  protected CreateFileFromTemplateDialog(@NotNull Project project, @NotNull PsiDirectory defaultDirectory) {
    super(project);
    FormScalingUtil.scaleComponentTree(this.getClass(), myPanel);

    setTitle(IdeBundle.message("action.create.new.class"));
    myKindLabel.setLabelFor(myKindCombo);
    myVisibilityLabel.setLabelFor(myPublicRadioButton);
    myModifiersLabel.setLabelFor(myNoModifierRadioButton);
    myNameLabel.setLabelFor(myNameField);

    myProject = project;
    myInputValidator = new CreateNewClassDialogValidatorExImpl(myProject);
    myDefaultPsiPackage =
      JavaPsiFacade.getInstance(project).findPackage(JavaDirectoryService.getInstance().getPackage(defaultDirectory).getQualifiedName());
    myFragmentFactory = JavaCodeFragmentFactory.getInstance(project);
    myPsiDocumentManager = PsiDocumentManager.getInstance(myProject);

    mySuperclassField = initAutocompleteEditorTextField("", "The superclass to explicitly extend, if any.");
    mySuperclassField.setName("superclass_editor_text_field");
    mySuperclassFieldPlaceholder.add(mySuperclassField);
    mySuperclassLabel.setLabelFor(mySuperclassField);

    myInterfacesField = initAutocompleteEditorTextField("", "The interface to implement, if any.");
    myInterfacesField.setName("interfaces_editor_text_field");
    myInterfacesFieldPlaceholder.add(myInterfacesField);
    myInterfacesLabel.setLabelFor(myInterfacesField);

    myPackageField = initAutocompleteEditorTextField(myDefaultPsiPackage.getQualifiedName(), "The package to create the item in.");
    myPackageFieldPlaceholder.add(myPackageField);
    myPackageLabel.setLabelFor(myPackageField);

    setKindComponentsVisible(false);

    init();
    initKindCombo();
    if (myKindCombo.getComboBox().getItemCount() > 1) {
      setKindComponentsVisible(true);
    }
  }

  @NotNull
  private EditorTextField initAutocompleteEditorTextField(@NotNull String defaultText, @NotNull String tooltip) {
    JavaCodeFragment fragment = myFragmentFactory.createReferenceCodeFragment(defaultText, myDefaultPsiPackage, true, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    Document doc = myPsiDocumentManager.getDocument(fragment);
    EditorTextField editorTextField = new EditorTextField(doc, myProject, StdFileTypes.JAVA);
    editorTextField.setToolTipText(tooltip);
    return editorTextField;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myInputValidator != null) {
      String nameText = myNameField.getText();
      String superclassAsString = mySuperclassField.getText();
      String packageText = myPackageField.getText();
      if (!myInputValidator.checkInput(nameText)) {
        String errorText = LangBundle.message("incorrect.name");
        String message = myInputValidator.getErrorText(nameText);
        if (message != null) {
          errorText = message;
        }

        return new ValidationInfo(errorText, myNameField);
      }

      Type superclassAsType = Type.newType(superclassAsString, myProject);
      if (mySuperclassField.isVisible() && (!superclassAsType.canUseAsClass() || !myInputValidator.checkSuperclass(superclassAsString))) {
        return new ValidationInfo(myInputValidator.getSuperclassErrorText(superclassAsString), mySuperclassField);
      }

      for (String interfaceAsString : Splitter.on(',').trimResults().split(getInterfaces())) {
        Type interfaceAsType = Type.newType(interfaceAsString, myProject);
        if (!interfaceAsType.canUseAsInterface() || !myInputValidator.checkInterface(interfaceAsString)) {
          return new ValidationInfo(myInputValidator.getInterfacesErrorText(interfaceAsString), myInterfacesField);
        }
      }

      if (!myInputValidator.checkPackage(packageText)) {
        return new ValidationInfo(myInputValidator.getPackageErrorText(packageText), myPackageField);
      }
    }
    return super.doValidate();
  }

  private void configureComponents(@Nullable Kind kind) {
    boolean isClassKind = (kind != null && kind == Kind.CLASS);

    if (isClassKind) {
      mySuperclassLabel.setVisible(true);
      mySuperclassFieldPlaceholder.setVisible(true);
      mySuperclassField.setFocusable(true);

      myModifiersLabel.setVisible(true);
      myNoModifierRadioButton.setSelected(true);
      myNoModifierRadioButton.setVisible(true);
      myAbstractRadioButton.setVisible(true);
      myFinalRadioButton.setVisible(true);

      myOverridesSeparator.setVisible(true);
      myShowSelectOverridesDialogCheckBox.setVisible(true);
    }
    else {
      mySuperclassLabel.setVisible(false);
      mySuperclassField.setText("");
      mySuperclassField.setFocusable(false);
      mySuperclassFieldPlaceholder.setVisible(false);

      myModifiersLabel.setVisible(false);
      myNoModifierRadioButton.setSelected(true);
      myNoModifierRadioButton.setVisible(false);
      myAbstractRadioButton.setVisible(false);
      myFinalRadioButton.setVisible(false);

      myOverridesSeparator.setVisible(false);
      myShowSelectOverridesDialogCheckBox.setSelected(false);
      myShowSelectOverridesDialogCheckBox.setVisible(false);
    }
  }

  private String getName() {
    String text = myNameField.getText().trim();
    myNameField.setText(text);
    return text;
  }

  public String getSuperclass() {
    String superclass = mySuperclassField.getText().trim();
    mySuperclassField.setText(superclass);
    return superclass;
  }

  public void setSuperclass(String superclass) {
    mySuperclassField.setText(superclass);
  }

  private String getPackage() {
    String packageName = myPackageField.getText().replace(" ", "");
    myPackageField.setText(packageName);
    return packageName;
  }

  public void setPackage(String packageName) {
    myPackageField.setText(packageName);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    List<String> imports = new ArrayList<String>();
    String localPackage = getPackage();
    String superclassAsString = getSuperclass();
    if (!superclassAsString.isEmpty()) {
      Type superclassAsType = Type.newType(superclassAsString, myProject);
      myCreationOptions.put(ATTRIBUTE_SUPERCLASS, superclassAsType.getClassWithNesting());
      if (superclassAsType.requiresImport(localPackage)) {
        imports.add(superclassAsType.getClassToImport());
      }
    }
    else {
      myCreationOptions.put(ATTRIBUTE_SUPERCLASS, "");
    }

    List<String> interfacesToUse = new ArrayList<String>();
    for (String interfaceAsString : Splitter.on(',').trimResults().split(getInterfaces())) {
      Type interfaceAsType = Type.newType(interfaceAsString, myProject);
      interfacesToUse.add(interfaceAsType.getClassWithNesting());
      if (interfaceAsType.requiresImport(localPackage)) {
        imports.add(interfaceAsType.getClassToImport());
      }
    }

    myCreationOptions.put(ATTRIBUTE_INTERFACES, Joiner.on(", ").join(interfacesToUse));
    myCreationOptions.put(FileTemplate.ATTRIBUTE_PACKAGE_NAME, localPackage);
    Visibility visibility = myPublicRadioButton.isSelected() ? Visibility.PUBLIC : Visibility.PACKAGE_PRIVATE;
    myCreationOptions.put(ATTRIBUTE_VISIBILITY, visibility.toString());
    myCreationOptions.put(ATTRIBUTE_ABSTRACT, Boolean.toString(myAbstractRadioButton.isSelected()).toUpperCase(Locale.ROOT));
    myCreationOptions.put(ATTRIBUTE_FINAL, Boolean.toString(myFinalRadioButton.isSelected()).toUpperCase(Locale.ROOT));
    myCreationOptions.put(ATTRIBUTE_IMPORT_BLOCK, formatImports(imports));
    if (myCreator != null && myCreator.tryCreate(getName()).length == 0) {
      return;
    }

    super.doOKAction();
  }

  @NotNull
  private static String formatImports(Iterable<String> imports) {
    StringBuilder importBlock = new StringBuilder();
    for (String entry : imports) {
      importBlock.append("import ").append(entry).append(";\n");
    }

    return importBlock.toString();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void setKindComponentsVisible(boolean visible) {
    myKindCombo.setVisible(visible);
    myKindLabel.setVisible(visible);
  }

  private void addKind(@NotNull Kind kind) {
    myKindCombo.addItem(kind.getName(), kind.getIcon(), kind.getTemplateName());
  }

  private void addKind(@NotNull FileTemplate template) {
    myKindCombo.addItem(template.getName(), JavaFileType.INSTANCE.getIcon(), template.getName());
  }

  PsiClass show(@NotNull final FileCreator creator) throws FailedToCreateFileException {
    final Ref<PsiClass> ref = Ref.create(null);
    myCreator = new ElementCreator(myProject, IdeBundle.message("title.cannot.create.class")) {
      @Override
      protected PsiElement[] create(String newName) throws Exception {
        PsiClass element = creator.createFile(getName(), myCreationOptions, myKindCombo.getSelectedName());
        ref.set(element);
        return element == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{element};
      }

      @Override
      protected String getActionName(String newName) {
        return creator.getActionName(newName, myKindCombo.getSelectedName());
      }
    };

    show();
    if (getExitCode() == OK_EXIT_CODE) {
      return ref.get();
    }
    else {
      throw new FailedToCreateFileException("Create returned a null object.");
    }
  }

  boolean isShowSelectOverridesDialogCheckBoxSelected() {
    return myShowSelectOverridesDialogCheckBox.isSelected();
  }

  String getInterfaces() {
    return myInterfacesField.getText();
  }

  void setInterfaces(String newInterface) {
    myInterfacesField.setText(newInterface);
  }

  public void initKindCombo() {
    myKindCombo.registerUpDownHint(myNameField);
    myKindCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (actionEvent.getSource().equals(myKindCombo.getComboBox())) {
          configureComponents(Kind.valueOfText(myKindCombo.getSelectedName()));
        }
      }
    });

    addKind(Kind.CLASS);
    addKind(Kind.INTERFACE);
    if (LanguageLevelProjectExtension.getInstance(myProject).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) {
      addKind(Kind.ENUM);
      addKind(Kind.ANNOTATION);
    }

    final JavaCreateFromTemplateHandler handler = new JavaCreateFromTemplateHandler();
    for (FileTemplate template : FileTemplateManager.getInstance(myProject).getAllTemplates()) {
      if (handler.handlesTemplate(template)) {
        addKind(template);
      }
    }
  }

  interface FileCreator {
    @Nullable
    PsiClass createFile(@NotNull String name, @NotNull Map<String, String> creationOptions, @NotNull String templateName);

    @NotNull
    String getActionName(@NotNull String name, @NotNull String templateName);
  }

  public enum Visibility {
    PUBLIC,
    PACKAGE_PRIVATE
  }

  public static abstract class Type {
    private static final String JAVA_LANG_PACKAGE = "java.lang";

    abstract String getClassWithNesting();

    abstract String getClassToImport();

    abstract String getPackage();

    abstract boolean canUseAsClass();

    abstract boolean canUseAsInterface();

    private static Type newType(@NotNull String qualifiedName, @NotNull Project project) {
      try {
        return new PsiBackedType(qualifiedName, project);
      }
      catch (IllegalArgumentException e) {
        return new StringBackedType(qualifiedName);
      }
    }

    @Override
    public boolean equals(Object object) {
      if (object instanceof Type) {
        Type qualifiedClass = (Type)object;
        return getClassWithNesting().equals(qualifiedClass.getClassWithNesting()) &&
               getPackage().equals(qualifiedClass.getPackage());
      }

      return false;
    }

    @Override
    public int hashCode() {
      int hashCode = 17;
      hashCode = 31 * hashCode + getClassWithNesting().hashCode();
      hashCode = 31 * hashCode + getPackage().hashCode();
      return hashCode;
    }

    private boolean requiresImport(String localPackage) {
      return !getPackage().equals(localPackage) && !getPackage().equals(JAVA_LANG_PACKAGE);
    }
  }

  private static class PsiBackedType extends Type {
    private final PsiClass myPsiClass;
    private final PsiPackage myPsiPackage;
    private final JavaDirectoryService myJavaDirectoryService;
    private final String myClassNameWithNesting;
    private final String myNameOfClassToImport;

    private PsiBackedType(@NotNull String className, @NotNull Project project) {
      myJavaDirectoryService = JavaDirectoryService.getInstance();
      myPsiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      if (myPsiClass == null) {
        throw new IllegalArgumentException(className);
      }

      PsiPackage psiPackage = null;
      for (PsiElement parent = myPsiClass.getParent(); parent != null; parent = parent.getParent()) {
        if (parent instanceof PsiDirectory) {
          PsiDirectory psiDirectory = (PsiDirectory)parent;
          psiPackage = myJavaDirectoryService.getPackage(psiDirectory);
          break;
        }
      }

      String classToImport = null;
      Deque<String> containingClasses = new ArrayDeque<String>();
      for (PsiClass psiClass = myPsiClass; psiClass != null; psiClass = psiClass.getContainingClass()) {
        classToImport = psiClass.getName();
        containingClasses.addFirst(psiClass.getName());
      }

      myClassNameWithNesting = Joiner.on(".").join(containingClasses);
      myPsiPackage = psiPackage;
      myNameOfClassToImport = classToImport;
    }

    @Override
    String getClassWithNesting() {
      return myClassNameWithNesting;
    }

    @Override
    String getClassToImport() {
      return myPsiPackage.getQualifiedName() + "." + myNameOfClassToImport;
    }

    @Override
    String getPackage() {
      if (myPsiPackage != null) {
        return myPsiPackage.getQualifiedName();
      }
      else {
        throw new IllegalStateException("myPsiPackage cannot be null for a PsiBackedType.");
      }
    }

    @Override
    boolean canUseAsClass() {
      return !myPsiClass.isInterface() && !myPsiClass.isEnum() && !myPsiClass.isAnnotationType();
    }

    @Override
    boolean canUseAsInterface() {
      return myPsiClass.isInterface();
    }
  }

  private static class StringBackedType extends Type {
    private final String myPackage;
    private final String myClass;

    private StringBackedType(@NotNull String className) {
      int lastDotIndex = className.lastIndexOf(".");
      if (lastDotIndex != -1) {
        myPackage = className.substring(0, lastDotIndex);
        myClass = className.substring(lastDotIndex + 1);
      }
      else {
        myPackage = "";
        myClass = className;
      }
    }

    @NotNull
    @Override
    String getClassWithNesting() {
      return myClass;
    }

    @Override
    String getClassToImport() {
      return getQualifiedClass();
    }

    @NotNull
    @Override
    String getPackage() {
      return myPackage;
    }

    @NotNull
    String getQualifiedClass() {
      return myPackage.isEmpty() ? myClass : myPackage + "." + myClass;
    }

    @Override
    boolean canUseAsClass() {
      return true;
    }

    @Override
    boolean canUseAsInterface() {
      return true;
    }
  }

  static class FailedToCreateFileException extends Exception {
    FailedToCreateFileException(String message) {
      super(message);
    }
  }
}
