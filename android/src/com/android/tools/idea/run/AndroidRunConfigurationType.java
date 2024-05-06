// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.run;

import com.android.tools.idea.help.AndroidWebHelpProvider;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.*;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import icons.StudioIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public final class AndroidRunConfigurationType extends ConfigurationTypeBase {
  public static final String ID = "AndroidRunConfigurationType";

  public AndroidRunConfigurationType() {
    super(ID,
          AndroidBundle.message("android.run.configuration.type.name"),
          AndroidBundle.message("android.run.configuration.type.description"),
          NotNullLazyValue.createValue(() -> StudioIcons.Shell.Filetree.ANDROID_PROJECT));

    addFactory(new AndroidRunConfigurationFactory());
  }

  @Override
  public String getHelpTopic() {
    return AndroidWebHelpProvider.HELP_PREFIX + "r/studio-ui/rundebugconfig.html";
  }

  public class AndroidRunConfigurationFactory extends AndroidRunConfigurationFactoryBase {
    public AndroidRunConfigurationFactory() {
      super(AndroidRunConfigurationType.this);
    }

    @Override
    @NotNull
    public String getId() {
      // This ID must be non-localized, use a rae string instead of the message bundle string.
      return "Android App";
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new AndroidRunConfiguration(project, this);
    }
  }

  public static AndroidRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidRunConfigurationType.class);
  }

  public ConfigurationFactory getFactory() {
    return getConfigurationFactories()[0];
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
