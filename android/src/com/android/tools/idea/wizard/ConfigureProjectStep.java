/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.NewProjectWizardState.*;

/**
 * ConfigureProjectStep is the first page in the New Project wizard that sets project name, location, and other project-global parameters.
 */
public class ConfigureProjectStep extends TemplateWizardStep {
  private static final Logger LOG = Logger.getInstance("#" + ConfigureProjectStep.class.getName());
  private static final String SAMPLE_PACKAGE_PREFIX = "com.example.";

  private JTextField myProjectLocation;
  private JTextField myAppName;
  private JTextField myPackageName;
  private JComboBox myMinSdk;
  private JComboBox myTargetSdk;
  private JComboBox myCompileWith;
  private JComboBox myTheme;
  private JCheckBox myCreateCustomLauncherIconCheckBox;
  private JCheckBox myCreateActivityCheckBox;
  private JCheckBox myLibraryCheckBox;
  private JPanel myPanel;
  private JTextField myProjectName;
  private JLabel myDescription;
  private JLabel myError;

  public ConfigureProjectStep(TemplateWizard templateWizard, NewProjectWizardState state) {
    super(templateWizard, state);

    IAndroidTarget[] targets = getCompilationTargets();
    String[] knownVersions = TemplateUtils.getKnownVersions();
    for (int i = 0; i < knownVersions.length; i++) {
      AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(knownVersions[i], i + 1);
      myMinSdk.addItem(targetInfo);
      myTargetSdk.addItem(targetInfo);
    }
    for (IAndroidTarget target : targets) {
      AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(target);
      myTemplateState.put(ATTR_BUILD_API, targetInfo.apiLevel);
      myCompileWith.addItem(targetInfo);
      if (target.getVersion().isPreview()) {
        myMinSdk.addItem(targetInfo);
        myTargetSdk.addItem(targetInfo);
      }
    }

    TemplateMetadata metadata = myTemplateState.getTemplateMetadata();
    if (metadata != null) {
      Parameter param = metadata.getParameter(ATTR_BASE_THEME);
      if (param != null && param.element != null) {
        populateComboBox(myTheme, param);
        register(ATTR_BASE_THEME, myTheme);
      }
    }

    register(ATTR_PROJECT_NAME, myProjectName);
    register(ATTR_PROJECT_LOCATION, myProjectLocation);
    register(ATTR_APP_TITLE, myAppName);
    register(ATTR_PACKAGE_NAME, myPackageName);
    register(ATTR_MIN_API, myMinSdk);
    register(ATTR_TARGET_API, myTargetSdk);
    register(ATTR_BUILD_API, myCompileWith);
    register(ATTR_CREATE_ACTIVITY, myCreateActivityCheckBox);
    register(ATTR_CREATE_ICONS, myCreateCustomLauncherIconCheckBox);
    register(ATTR_LIBRARY, myLibraryCheckBox);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  private IAndroidTarget[] getCompilationTargets() {
    IAndroidTarget[] targets = AndroidSdkUtils.tryToChooseAndroidSdk().getTargets();
    List<IAndroidTarget> list = new ArrayList<IAndroidTarget>();

    for (IAndroidTarget target : targets) {
      if (target.isPlatform() == false &&
          (target.getOptionalLibraries() == null ||
           target.getOptionalLibraries().length == 0)) {
        continue;
      }
      list.add(target);
    }

    return list.toArray(new IAndroidTarget[list.size()]);
  }

  @Override
  @Nullable
  public String getHelpText(@NotNull String param) {
    if (param.equals(ATTR_PROJECT_NAME)) {
      return "This project name is used only by the IDE. It can typically be the same as the application name.";
    } else if (param.equals(ATTR_APP_TITLE)) {
      return "The application name is shown in the Play store, as well as in the Manage Applications list in Settings.";
    } else if (param.equals(ATTR_PACKAGE_NAME)) {
      return "The package name must be a unique identifier for your application.\n It is typically not shown to users, " +
             "but it <b>must</b> stay the same for the lifetime of your application; it is how multiple versions of the same application " +
             "" +
             "are" +
             ""  +
             "considered the \"same app\".\nThis is typically the reverse domain name of your organization plus one or more application " +
             "identifiers, and it must be a valid Java package name.";
    } else if (param.equals(ATTR_MIN_API)) {
      return "Choose the lowest version of Android that your application will support. Lower API levels target more devices, " +
             "but means fewer features are available. By targeting API 8 and later, you reach approximately 95% of the market.";
    } else if (param.equals(ATTR_TARGET_API)) {
      return "Choose the highest API level that the application is known to work with. This attribute informs the system that you have " +
             "tested against the target version and the system should not enable any compatibility behaviors to maintain your app's " +
             "forward-compatibility with the target version. The application is still able to run on older versions (down to " +
             "minSdkVersion). Your application may look dated if you are not targeting the current version.";
    } else if (param.equals(ATTR_BUILD_API)) {
      return "Choose a target API to compile your code against, from your installed SDKs. This is typically the most recent version, " +
             "or the first version that supports all the APIs you want to directly access without reflection.";
    } else if (param.equals(ATTR_BASE_THEME)) {
      return "Choose the base theme to use for the application";
    } else {
      return null;
    }
  }

  @Override
  public void onStepLeaving() {
    ((NewProjectWizardState)myTemplateState).updateParameters();
  }

  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @Override
  protected JLabel getError() {
    return myError;
  }

  @Override
  public boolean validate() {
    if (myIgnoreUpdates) {
      return true;
    }
    if (!super.validate()) {
      return false;
    }

    ((NewProjectWizardState)myTemplateState).updateParameters();

    SwingUtilities.invokeLater(new Runnable() { @Override public void run() {
      updateDerivedValue(ATTR_APP_TITLE, myAppName, new Callable<String>() {
        @Override
        public String call() {
          return computeAppName();
        }
      });
      updateDerivedValue(ATTR_PROJECT_NAME, myProjectName, new Callable<String>() {
        @Override
        public String call() {
          return computeProjectName();
        }
      });
      updateDerivedValue(ATTR_PACKAGE_NAME, myPackageName, new Callable<String>() {
        @Override
        public String call() {
          return computePackageName();
        }
      });
      updateDerivedValue(ATTR_PROJECT_LOCATION, myProjectLocation, new Callable<String>() {
        @Override
        public String call() {
          return computeProjectLocation();
        }
      });
    }});

    AndroidTargetComboBoxItem item = (AndroidTargetComboBoxItem)myMinSdk.getSelectedItem();
    if (item != null) {
      myTemplateState.put(ATTR_MIN_API_LEVEL, item.apiLevel);
    }

    setErrorHtml("");
    String applicationName = (String)myTemplateState.get(ATTR_APP_TITLE);
    if (applicationName == null || applicationName.isEmpty()) {
      setErrorHtml("Enter an application name (shown in launcher)");
      return false;
    }
    if (Character.isLowerCase(applicationName.charAt(0))) {
      setErrorHtml("The application name for most apps begins with an uppercase letter");
    }
    String packageName = (String)myTemplateState.get(ATTR_PACKAGE_NAME);
    if (packageName == null || packageName.isEmpty()) {
      setErrorHtml("Package name must be specified.");
    } else if (packageName.startsWith(SAMPLE_PACKAGE_PREFIX)) {
      setErrorHtml(String.format("The prefix '%1$s' is meant as a placeholder and should " +
                                    "not be used", SAMPLE_PACKAGE_PREFIX));
    }

    Integer minSdk = (Integer)myTemplateState.get(ATTR_MIN_API);
    if (minSdk == null) {
      setErrorHtml("Select a minimum SDK version");
      return false;
    }
    // TODO: Properly handle preview versions
    int minLevel = (Integer)myTemplateState.get(ATTR_MIN_API_LEVEL);
    int buildLevel = (Integer)myTemplateState.get(ATTR_BUILD_API);
    int targetLevel = (Integer)myTemplateState.get(ATTR_TARGET_API);
    if (targetLevel < minLevel) {
      setErrorHtml("The target SDK version should be at least as high as the minimum SDK version");
      return false;
    }
    if (buildLevel < minLevel) {
      setErrorHtml("The build target version should be at least as high as the minimum SDK version");
      return false;
    }

    String projectLocation = (String)myTemplateState.get(ATTR_PROJECT_LOCATION);
    if (projectLocation == null || projectLocation.isEmpty()) {
      setErrorHtml("The project location must be specified");
      return false;
    }
    File file = new File(projectLocation);
    if (file.exists()) {
      setErrorHtml("The project location must not already exist");
      return false;
    }
    if (file.getParent() == null) {
      setErrorHtml("The project location can not be at the filesystem root");
      return false;
    }
    if (!file.getParentFile().exists() || !file.getParentFile().isDirectory()) {
      setErrorHtml("The project location's parent directory must already exist");
      return false;
    }

    return true;
  }

  private void updateDerivedValue(@NotNull String attrName, @NotNull JTextField textField, @NotNull Callable<String> valueDeriver) {
    try {
      myIgnoreUpdates = true;
      if (!myTemplateState.myModified.contains(attrName)) {
        String s = valueDeriver.call();
        if (s != null && !s.equals(myTemplateState.get(attrName))) {
          myTemplateState.put(attrName, s);
          textField.setText(s);
          myTemplateState.myModified.remove(attrName);
        }
      }
    }
    catch (Exception e) {
    }
    finally {
      myIgnoreUpdates = false;
    }
  }

  @NotNull
  private String computePackageName() {
    String projectName = (String)myTemplateState.get(ATTR_PROJECT_NAME);
    if (projectName != null && !projectName.isEmpty()) {
      return SAMPLE_PACKAGE_PREFIX + projectName;
    } else {
      return "";
    }
  }

  @NotNull
  private String computeProjectName() {
    String name = (String)myTemplateState.get(ATTR_APP_TITLE);
    if (name == null) {
      return "";
    }
    return name.toLowerCase();
  }

  @NotNull
  private String computeAppName() {
    return (String)myTemplateState.get(ATTR_PROJECT_NAME);
  }

  @NotNull
  private String computeProjectLocation() {
    return new File(NewProjectWizardState.getProjectFileDirectory(), (String)myTemplateState.get(ATTR_PROJECT_NAME))
      .getAbsolutePath();
  }

  public static class AndroidTargetComboBoxItem extends ComboBoxItem {
    public int apiLevel = -1;
    public IAndroidTarget target = null;

    public AndroidTargetComboBoxItem(@NotNull String label, int apiLevel) {
      super(apiLevel, label, 1, 1);
      this.apiLevel = apiLevel;
    }

    public AndroidTargetComboBoxItem(@NotNull IAndroidTarget target) {
      super(getId(target), getLabel(target), 1, 1);
      this.target = target;
      apiLevel = target.getVersion().getApiLevel();
    }

    @NotNull
    private static String getLabel(@NotNull IAndroidTarget target) {
      if (target.isPlatform()
          && target.getVersion().getApiLevel() <= SdkVersionInfo.HIGHEST_KNOWN_API) {
        return SdkVersionInfo.getAndroidName(target.getVersion().getApiLevel());
      } else {
        return TemplateUtils.getTargetLabel(target);
      }
    }

    @NotNull
    private static Object getId(@NotNull IAndroidTarget target) {
      if (target.getVersion().isPreview()) {
        return target.getVersion().getCodename();
      } else {
        return target.getVersion().getApiLevel();
      }
    }

    @Override
    public String toString() {
      return label;
    }
  }
}
