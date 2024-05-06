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
package com.android.tools.idea.run;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidConfigurationProducer extends JavaRunConfigurationProducerBase<AndroidRunConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return AndroidRunConfigurationType.getInstance().getFactory();
  }

  @Nullable
  private static PsiClass getActivityClass(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null)
      return null;
    final Module module = context.getModule();
    if (module == null) return null;
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(element.getProject());
    GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(true);
    PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, scope);
    if (activityClass == null) return null;

    PsiClass elementClass = AndroidPsiUtils.getPsiParentOfType(element, PsiClass.class, false);
    while (elementClass != null) {
      if (elementClass.isInheritor(activityClass, true)) {
        return elementClass;
      }
      elementClass = PsiTreeUtil.getParentOfType(elementClass, PsiClass.class);
    }
    return null;
  }

  @Nullable
  @Override
  public ConfigurationFromContext createConfigurationFromContext(@NotNull ConfigurationContext context) {
    return getActivityClass(context) == null ? null : super.createConfigurationFromContext(context);
  }

  @Override
  protected boolean setupConfigurationFromContext(
    @NotNull AndroidRunConfiguration configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement) {

    final PsiClass activity = getActivityClass(context);

    if (activity == null) {
      return false;
    }
    final String activityName = activity.getQualifiedName();

    if (activityName == null) {
      return false;
    }
    sourceElement.set(activity);

    configuration.setLaunchActivity(activityName);
    configuration.setName(JavaExecutionUtil.getPresentableClassName(activityName));
    setupConfigurationModule(context, configuration);

    final TargetSelectionMode targetSelectionMode = AndroidUtils
      .getDefaultTargetSelectionMode(context.getModule(), AndroidRunConfigurationType.getInstance(),
                                     AndroidTestRunConfigurationType.getInstance());
    if (targetSelectionMode != null) {
      configuration.getDeployTargetContext().setTargetSelectionMode(targetSelectionMode);
    }
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull AndroidRunConfiguration configuration, @NotNull ConfigurationContext context) {
    final PsiClass activity = getActivityClass(context);
    if (activity == null) {
      return false;
    }

    final String activityName = activity.getQualifiedName();
    if (activityName == null) {
      return false;
    }

    final Module contextModule = AndroidUtils.getAndroidModule(context);
    final Module confModule = configuration.getConfigurationModule().getModule();
    return Objects.equals(contextModule, confModule) && configuration.isLaunchingActivity(activityName);
  }
}
