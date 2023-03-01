/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.android.uipreview;

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX;
import static com.android.SdkConstants.GOOGLE_SUPPORT_ARTIFACT_PREFIX;
import static com.android.tools.lint.checks.AnnotationDetectorKt.RESTRICT_TO_ANNOTATION;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.module.Module;
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
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Predicate;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChooseClassDialog extends DialogWrapper implements ListSelectionListener {
  private final JList<PsiClass> myList = new JBList<>();
  private final JScrollPane myComponent = ScrollPaneFactory.createScrollPane(myList);
  private final Predicate<PsiClass> myFilter;
  private String myResultClassName;

  private ChooseClassDialog(Module module, String title, boolean includeAll, @Nullable Predicate<PsiClass> filter, String... classes) {
    super(module.getProject());
    myFilter = filter;

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        if (myList.getSelectedValue() != null) {
          close(OK_EXIT_CODE);
          return true;
        }
        return false;
      }
    }.installOn(myList);

    DefaultListModel<PsiClass> model = new DefaultListModel<>();
    findClasses(module, includeAll, model, classes);
    myList.setModel(model);
    //noinspection unchecked
    myList.setCellRenderer(new DefaultPsiElementCellRenderer());

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

    Dimension size = myComponent.getPreferredSize();
    size.height = myList.getPreferredSize().height + 20;
    myComponent.setPreferredSize(size);
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

  protected void findClasses(Module module, boolean includeAll, DefaultListModel model, String[] classes) {
    Collection<PsiClass> collection = new ArrayList<>(classes.length);

    for (String className : classes) {
      for (PsiClass psiClass : findInheritors(module, className, includeAll)) {
        if (myFilter != null && !myFilter.test(psiClass)) {
          continue;
        }

        collection.add(psiClass);
      }
    }

    Collator collator = Collator.getInstance(Locale.US);

    collection.stream()
      .sorted((psiClass1, psiClass2) -> collator.compare(SymbolPresentationUtil.getSymbolPresentableText(psiClass1),
                                                         SymbolPresentationUtil.getSymbolPresentableText(psiClass2)))
      .forEach(psiClass -> {
        // noinspection Convert2MethodRef, unchecked
        model.addElement(psiClass);
      });
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
      dumbService.showDumbModeNotification(String.format("%1$s are not available while indices are updating.", title));
      return null;
    }

    ChooseClassDialog dialog = new ChooseClassDialog(module, title, true, filter, classes);
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
