// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.uipreview;

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX;
import static com.android.SdkConstants.GOOGLE_SUPPORT_ARTIFACT_PREFIX;
import static com.android.tools.lint.checks.AnnotationDetectorKt.RESTRICT_TO_ANNOTATION;

import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChooseClassDialog extends DialogWrapper implements ListSelectionListener {
  private final JList<PsiClass> myList = new JBList<>();
  private final JComponent myComponent =
    new JBScrollPane(myList, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
  private String myResultClassName;

  private ChooseClassDialog(Module module, String title, @NotNull Collection<PsiClass> classes) {
    super(module.getProject());

    new DoubleClickListener() {
      @Override
      public boolean onDoubleClick(@NotNull MouseEvent e) {
        if (myList.getSelectedValue() != null) {
          close(OK_EXIT_CODE);
          return true;
        }
        return false;
      }
    }.installOn(myList);

    // The cell renderer is calculated in the background when using PsiClassListCellRenderer so, by the time the dialog
    // opens we can not yet calculate the right width. With this, we ensure that it is visible in all conditions.
    myComponent.setPreferredSize(JBUI.size(900, 300));

    DefaultListModel<PsiClass> model = new DefaultListModel<>();
    model.addAll(classes);

    myList.setModel(model);
    myList.setCellRenderer(new PsiClassListCellRenderer());

    ListSelectionModel selectionModel = myList.getSelectionModel();
    selectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    selectionModel.addListSelectionListener(this);

    ListSpeedSearch<PsiClass> search = new ListSpeedSearch<>(myList, null, null) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        PsiClass psiClass = (PsiClass)element;
        assert psiClass.getName() != null && psiClass.getQualifiedName() != null;
        return compare(psiClass.getName(), pattern) || compare(psiClass.getQualifiedName(), pattern);
      }
    };
    search.setupListeners();

    setTitle(title);
    setOKActionEnabled(false);

    init();
  }

  private void setSelectedClass(@NotNull String className) {
    ListModel<PsiClass> model = myList.getModel();
    for (int index = 0; index < myList.getModel().getSize(); index++) {
      if (className.equals(model.getElementAt(index).getQualifiedName())) {
        myList.setSelectedIndex(index);
        break;
      }
    }
  }

  @NotNull
  protected static Collection<PsiClass> findClasses(@NotNull Module module,
                                                    boolean includeAll,
                                                    @NotNull Predicate<PsiClass> filter,
                                                    @NotNull String[] classes) {
    Collection<PsiClass> collection = new ArrayList<>(classes.length);

    for (String className : classes) {
      for (PsiClass psiClass : findInheritors(module, className, includeAll)) {
        if (!filter.test(psiClass)) {
          continue;
        }

        collection.add(psiClass);
      }
    }

    Collator collator = Collator.getInstance(Locale.US);

    return collection.stream()
      .sorted((psiClass1, psiClass2) -> collator.compare(SymbolPresentationUtil.getSymbolPresentableText(psiClass1),
                                                         SymbolPresentationUtil.getSymbolPresentableText(psiClass2)))
      .collect(Collectors.toUnmodifiableList());
  }

  private static Collection<PsiClass> findInheritors(Module module, String name, boolean includeAll) {
    PsiClass base = findClass(module, name);
    if (base != null) {
      GlobalSearchScope scope = includeAll ?
                                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false) :
                                GlobalSearchScope.moduleScope(module);
      Collection<PsiClass> classes;
      try {
        classes = ClassInheritorsSearch.search(base, scope, true).findAll();
      }
      catch (IndexNotReadyException e) {
        classes = Collections.emptyList();
      }
      return classes;
    }
    return Collections.emptyList();
  }

  @Nullable
  public static PsiClass findClass(Module module, @Nullable String name) {
    if (name == null) {
      return null;
    }
    Project project = module.getProject();
    PsiClass aClass;
    try {
      aClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
    }
    catch (IndexNotReadyException e) {
      aClass = null;
    }
    return aClass;
  }

  /**
   * Open a dialog if indices are available, otherwise show an error message.
   *
   * @return class name if user has selected one, null otherwise
   */
  @Nullable
  public static String openDialog(@NotNull Module module,
                                  @NotNull String title,
                                  @Nullable String currentValue,
                                  @Nullable Predicate<PsiClass> filter,
                                  @NotNull String... classes) {
    final Project project = module.getProject();
    final DumbService dumbService = DumbService.getInstance(project);
    if (dumbService.isDumb()) {
      // Variable "title" contains a string like "Views", "Fragments".
      dumbService.showDumbModeNotificationForFunctionality(String.format("%1$s are not available while indices are updating.", title),
                                                           DumbModeBlockedFunctionality.Android);
      return null;
    }

    Collection<PsiClass> filteredClasses = findClasses(module, true, filter != null ? filter : aClass -> true, classes);
    ChooseClassDialog dialog = new ChooseClassDialog(module, title, filteredClasses);
    if (currentValue != null) {
      dialog.setSelectedClass(currentValue);
    }
    if (!dialog.hasChoices()) {
      String emptyErrorTitle = "No " + title + " Found";
      String emptyErrorMessage = "You must first create one or more " + title + " in code";
      Messages.showErrorDialog(emptyErrorMessage, emptyErrorTitle);
      return null;
    }
    return dialog.showAndGet() ? dialog.getClassName() : null;
  }

  /**
   * Returns a {@link PsiClass} filter that is true if the class is public and does not have a
   * "RestrictTo" annotation which limits the use of the class to a smaller non public scope.
   */
  @NotNull
  public static Predicate<PsiClass> getIsPublicAndUnrestrictedFilter() {
    return psiClass -> {
      PsiModifierList modifiers = psiClass.getModifierList();
      if (modifiers == null) {
        return false;
      }
      if (!modifiers.hasModifierProperty(PsiModifier.PUBLIC)) {
        return false;
      }
      for (PsiAnnotation annotation : modifiers.getAnnotations()) {
        if (RESTRICT_TO_ANNOTATION.isEquals(annotation.getQualifiedName())) {
          return false;
        }
      }
      return true;
    };
  }

  @NotNull
  public static Predicate<PsiClass> qualifiedNameFilter(@NotNull Predicate<String> filter) {
    return psiClass -> {
      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName == null) {
        return false;
      }
      return filter.test(qualifiedName);
    };
  }

  @NotNull
  public static Predicate<String> getIsUserDefinedFilter() {
    return qualifiedName -> !qualifiedName.startsWith(ANDROID_PKG_PREFIX) &&
                            !qualifiedName.startsWith(ANDROID_SUPPORT_PKG_PREFIX) &&
                            !qualifiedName.startsWith(ANDROIDX_PKG_PREFIX) &&
                            !qualifiedName.startsWith(GOOGLE_SUPPORT_ARTIFACT_PREFIX);
  }

  @NotNull
  public static Predicate<PsiClass> getUserDefinedPublicAndUnrestrictedFilter() {
    return getIsPublicAndUnrestrictedFilter().and(qualifiedNameFilter(getIsUserDefinedFilter()));
  }

  private boolean hasChoices() {
    return myList.getModel().getSize() > 0;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  public String getClassName() {
    return myResultClassName;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    PsiClass psiClass = myList.getSelectedValue();
    setOKActionEnabled(psiClass != null);
    myResultClassName = psiClass == null ? null : psiClass.getQualifiedName();
  }
}
