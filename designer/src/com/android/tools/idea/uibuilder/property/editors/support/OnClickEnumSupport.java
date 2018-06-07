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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.MergedManifest;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.android.SdkConstants.CLASS_ACTIVITY;

public class OnClickEnumSupport extends EnumSupport {

  public OnClickEnumSupport(@NotNull NlProperty property) {
    super(property);
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    Module module = myProperty.getModel().getModule();
    Configuration configuration = myProperty.getModel().getConfiguration();
    String activityClassName = configuration.getActivity();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    Collection<PsiClass> classes;
    if (activityClassName != null) {
      if (activityClassName.startsWith(".")) {
        MergedManifest manifest = MergedManifest.get(module);
        String pkg = StringUtil.notNullize(manifest.getPackage());
        activityClassName = pkg + activityClassName;
      }
      PsiClass activityClass = facade.findClass(activityClassName, module.getModuleScope());
      if (activityClass != null) {
        classes = Collections.singletonList(activityClass);
      } else {
        classes = Collections.emptyList();
      }
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      PsiClass activityClass = facade.findClass(CLASS_ACTIVITY, scope);
      if (activityClass != null) {
        scope = GlobalSearchScope.moduleScope(module);
        classes = ClassInheritorsSearch.search(activityClass, scope, true).findAll();
      }
      else {
        classes = Collections.emptyList();
      }
    }
    List<ValueWithDisplayString> values = new ArrayList<>();
    Set<String> found = new HashSet<>();
    for (PsiClass psiClass : classes) {
      for (PsiMethod method : psiClass.getMethods()) {
        if (psiClass.equals(method.getContainingClass()) &&
            OnClickConverter.CONVERTER_FOR_LAYOUT.checkSignature(method) &&
            found.add(method.getName())) {
          values.add(new ValueWithDisplayString(method.getName(), method.getName(), psiClass.getName()));
        }
      }
    }
    return values;
  }
}
