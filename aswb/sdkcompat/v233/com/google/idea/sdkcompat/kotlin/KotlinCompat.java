package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.arguments.Freezable;
import org.jetbrains.kotlin.cli.common.arguments.FreezableKt;
import org.jetbrains.kotlin.config.ApiVersion;
import org.jetbrains.kotlin.config.LanguageFeature;
import org.jetbrains.kotlin.config.LanguageFeature.State;
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup;
import org.jetbrains.kotlin.idea.configuration.ConfigureKotlinStatus;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurator;
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector;
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor;
import org.jetbrains.kotlin.platform.TargetPlatform;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;

/** Provides SDK compatibility shims for Kotlin classes, available to IntelliJ CE & UE. */
public class KotlinCompat {
  private KotlinCompat() {}

  /** #api213 inline in BlazeKotlinSyncPlugin using FreezableKt.unfrozen() */
  public static <T extends Freezable> Freezable unfreezeSettings(T settings) {
    return FreezableKt.unfrozen(settings);
  }

  /* #api213: inline in {@link BlazeKotlinSyncPlugin}*/
  public static void configureModule(Project project, Module module) {
    KotlinJavaModuleConfigurator configurator =
        KotlinJavaModuleConfigurator.Companion.getInstance();
    NotificationMessageCollector collector =
        new NotificationMessageCollector(project, "Configuring Kotlin", "Configuring Kotlin");
    Application application = ApplicationManager.getApplication();

    application.invokeAndWait(
        () -> {
          configurator.getOrCreateKotlinLibrary(project, collector);
        });

    List<Function0<Unit>> writeActions = new ArrayList<>();
    application.runReadAction(
        () -> {
          configurator.configureModule(module, collector, writeActions);
        });

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // do not invoke it later since serviceContainer may be disposed before it get completed
      application.invokeAndWait(
          () -> {
            writeActions.stream().forEach(Function0::invoke);
          });
    } else {
      application.invokeLater(
          () -> {
            writeActions.stream().forEach(Function0::invoke);
          });
    }
  }

  /** A Kotlin project configurator base.. */
  public abstract static class KotlinProjectConfiguratorBase implements KotlinProjectConfigurator {

    @NotNull
    @Override
    public String getName() {
      return "Bazel";
    }

    @NotNull
    @Override
    public String getPresentableText() {
      return "Bazel";
    }

    @NotNull
    @Override
    public TargetPlatform getTargetPlatform() {
      return JvmPlatforms.INSTANCE.getUnspecifiedJvmPlatform();
    }

    @Override
    public void addLibraryDependency(
        @NotNull Module module,
        @NotNull PsiElement psiElement,
        @NotNull ExternalLibraryDescriptor externalLibraryDescriptor,
        @NotNull LibraryJarDescriptor libraryJarDescriptor,
        @NotNull DependencyScope dependencyScope) {}

    @Override
    public void changeGeneralFeatureConfiguration(
        @NotNull Module module,
        @NotNull LanguageFeature languageFeature,
        @NotNull State state,
        boolean b) {}

    @Override
    public void configure(@NotNull Project project, @NotNull Collection<Module> collection) {}

    @NotNull
    @Override
    public ConfigureKotlinStatus getStatus(@NotNull ModuleSourceRootGroup moduleSourceRootGroup) {
      return getStatus(moduleSourceRootGroup.getBaseModule());
    }

    @NotNull
    public abstract ConfigureKotlinStatus getStatus(@NotNull Module module);

    @Override
    public void updateLanguageVersion(
        @NotNull Module module,
        @Nullable String s,
        @Nullable String s1,
        @NotNull ApiVersion apiVersion,
        boolean b) {}
  }
}
