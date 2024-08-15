/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.producers;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.ListSelectionModel;

/**
 * Pop up a dialog to choose a child test class. Called when creating a run configuration from an
 * abstract (or non-abstract super-class) test class/method.
 */
public class SubclassTestChooser {

  static void chooseSubclass(
      ConfigurationContext context,
      PsiClass testClass,
      Consumer<PsiClass> callbackOnClassSelection) {
    List<PsiClass> classes = findTestSubclasses(testClass);
    if (!testClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      classes.add(testClass);
    }
    if (classes.isEmpty()) {
      return;
    }
    if (classes.size() == 1) {
      callbackOnClassSelection.accept(classes.get(0));
      return;
    }
    PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
    classes.sort(renderer.getComparator());
    JBPopupFactory.getInstance()
        .createPopupChooserBuilder(classes)
        .setTitle("Choose test class to run")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(false)
        .setRenderer(renderer)
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setItemChosenCallback(callbackOnClassSelection::accept)
        .createPopup()
        .showInBestPositionFor(context.getDataContext());
  }

  static List<PsiClass> findTestSubclasses(PsiClass testClass) {
    return ClassInheritorsSearch.search(testClass).findAll().stream()
        .filter(ProducerUtils::isTestClass)
        .collect(Collectors.toList());
  }
}
