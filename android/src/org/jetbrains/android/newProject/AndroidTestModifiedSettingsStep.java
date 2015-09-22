package org.jetbrains.android.newProject;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.TargetSelectionMode;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
class AndroidTestModifiedSettingsStep extends AndroidModifiedSettingsStep {
  private final AndroidModulesComboBox myModulesCombo;
  private final Project myProject;

  public AndroidTestModifiedSettingsStep(@NotNull AndroidModuleBuilder builder, @NotNull SettingsStep settingsStep) {
    super(builder, settingsStep);
    myModulesCombo = new AndroidModulesComboBox();
    myProject = settingsStep.getContext().getProject();
    assert myProject != null : "test module can't be created as first module";
    myModulesCombo.init(myProject);
    settingsStep.addSettingsField("\u001BTested module: ", myModulesCombo);
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    final Module testedModule = myModulesCombo.getModule();
    myBuilder.setTestedModule(testedModule);
    myBuilder.setTargetSelectionMode(chooseTargetSelectionMode(testedModule));
  }

  @NotNull
  private TargetSelectionMode chooseTargetSelectionMode(@NotNull Module testedModule) {
    final List<RunConfiguration> androidConfigurations =
      RunManager.getInstance(myProject).getConfigurationsList(AndroidRunConfigurationType.getInstance());

    for (RunConfiguration configuration : androidConfigurations) {
      final AndroidRunConfiguration cfg = (AndroidRunConfiguration)configuration;
      final Module module = cfg.getConfigurationModule().getModule();

      if (testedModule.equals(module)) {
        return cfg.getTargetSelectionMode();
      }
    }
    return TargetSelectionMode.EMULATOR;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    if (!super.validate()) return false;
    final Module module = myModulesCombo.getModule();

    if (module == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.specify.tested.module.error"));
    }
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.tested.module.without.facet.error"));
    }
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    if (moduleDirPath == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.cannot.find.module.parent.dir.error", module.getName()));
    }
    final VirtualFile mainContentRoot = AndroidRootUtil.getMainContentRoot(facet);
    if (mainContentRoot == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.cannot.find.main.content.root.error", module.getName()));
    }
    return true;
  }
}
