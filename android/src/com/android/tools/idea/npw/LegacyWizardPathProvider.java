package com.android.tools.idea.npw;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;

/**
 * Integrates old paths into the new module wizard.
 */
public class LegacyWizardPathProvider implements NewModuleDynamicPathFactory {
  @Override
  public Collection<NewModuleDynamicPath> createWizardPaths(@Nullable Project project, @NotNull Disposable disposable) {
    LegacyWizardModuleBuilder builder = new LegacyWizardModuleBuilder(project, disposable);
    Collection<LegacyPathWrapper> wrappers = builder.getWrappers();
    return ImmutableSet.copyOf(wrappers);
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface Migrated {
  }

  private static class LegacyWizardModuleBuilder extends TemplateWizardModuleBuilder {
    private final Collection<LegacyPathWrapper> myWrappers;

    public LegacyWizardModuleBuilder(@Nullable Project project, Disposable disposable) {
      super(null, null, project, null, Lists.newLinkedList(), disposable, false);
      myWrappers = wrapPaths();
    }

    private Collection<LegacyPathWrapper> wrapPaths() {
      List<LegacyPathWrapper> wrappers = ContainerUtil.newLinkedList();
      for (WizardPath wizardPath : getPaths()) {
        if (wizardPath.getClass().getAnnotation(Migrated.class) == null) {
          wrappers.add(new LegacyPathWrapper(myWizardState, wizardPath));
        }
      }
      return wrappers;
    }

    public Collection<LegacyPathWrapper> getWrappers() {
      return myWrappers;
    }

    @Override
    public void update() {
      super.update();
      if (myWrappers != null) {
        for (LegacyPathWrapper wrapper : myWrappers) {
          if (wrapper.isPathVisible()) {
            wrapper.updateWizard();
          }
        }
      }
    }
  }

}
