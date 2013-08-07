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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.NewProjectWizardState.*;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidModuleStep extends TemplateWizardStep {
  private static final Logger LOG = Logger.getInstance("#" + ConfigureAndroidModuleStep.class.getName());
  private static final String SAMPLE_PACKAGE_PREFIX = "com.example.";
  private static final String INVALID_FILENAME_CHARS = "[/\\\\?%*:|\"<>]";
  private static final Set<String> INVALID_MSFT_FILENAMES = ImmutableSet
    .of("con", "prn", "aux", "clock$", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
        "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "$mft", "$mftmirr", "$logfile", "$volume", "$attrdef", "$bitmap", "$boot",
        "$badclus", "$secure", "$upcase", "$extend", "$quota", "$objid", "$reparse");

  private TextFieldWithBrowseButton myProjectLocation;
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
  private JTextField myModuleName;
  private JLabel myDescription;
  private JLabel myError;
  private JLabel myProjectLocationLabel;
  private JLabel myModuleNameLabel;
  boolean myInitializedPackageNameText = false;

  public ConfigureAndroidModuleStep(TemplateWizardState state, @Nullable Project project, @Nullable Icon sidePanelIcon,
                                    UpdateListener updateListener) {
    super(state, project, sidePanelIcon, updateListener);

    IAndroidTarget[] targets = getCompilationTargets();

    if (AndroidSdkUtils.isAndroidSdkAvailable()) {
      String[] knownVersions = TemplateUtils.getKnownVersions();

      for (int i = 0; i < knownVersions.length; i++) {
        AndroidTargetComboBoxItem targetInfo = new AndroidTargetComboBoxItem(knownVersions[i], i + 1);
        myMinSdk.addItem(targetInfo);
        myTargetSdk.addItem(targetInfo);
      }
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

    registerUiElements();

    myProjectLocation.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor("Project location", "Please choose a location for your project");
        File currentPath = new File(myProjectLocation.getText());
        File parentPath = currentPath.getParentFile();
        if (parentPath == null) {
          parentPath = new File("/");
        }
        VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath);
        String filename = currentPath.getName();
        VirtualFileWrapper fileWrapper =
            FileChooserFactory.getInstance().createSaveFileDialog(fileSaverDescriptor, (Project)null).save(parent, filename);
        if (fileWrapper != null && fileWrapper.getFile() != null) {
          myProjectLocation.setText(fileWrapper.getFile().getAbsolutePath());
        }
      }
    });
    myProjectLocation.getTextField().addFocusListener(this);
    myProjectLocation.getTextField().getDocument().addDocumentListener(this);
    if (myTemplateState.myHidden.contains(ATTR_PROJECT_LOCATION)) {
      myProjectLocation.setVisible(false);
      myProjectLocationLabel.setVisible(false);
    }
    if (myTemplateState.myHidden.contains(ATTR_IS_LIBRARY_MODULE)) {
      myLibraryCheckBox.setVisible(false);
    }
    if (myTemplateState.myHidden.contains(ATTR_MODULE_NAME)) {
      myModuleName.setVisible(false);
      myModuleNameLabel.setVisible(false);
    }
  }

  private void registerUiElements() {
    TemplateMetadata metadata = myTemplateState.getTemplateMetadata();
    if (metadata != null) {
      Parameter param = metadata.getParameter(ATTR_BASE_THEME);
      if (param != null && param.element != null) {
        populateComboBox(myTheme, param);
        register(ATTR_BASE_THEME, myTheme);
      }
    }

    register(ATTR_MODULE_NAME, myModuleName);
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
  public void refreshUiFromParameters() {
    // It's easier to just re-register the UI elements instead of trying to set their values manually. Not all of the elements have
    // parameters in the template, and the super refreshUiFromParameters won't touch those elements.
    registerUiElements();
    super.refreshUiFromParameters();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAppName;
  }

  public void setModuleName(String name) {
    myModuleName.setText(name);
    myTemplateState.put(ATTR_MODULE_NAME, name);
    myTemplateState.myModified.add(ATTR_MODULE_NAME);
    validate();
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
    if (param.equals(ATTR_MODULE_NAME)) {
      return "This module name is used only by the IDE. It can typically be the same as the application name.";
    } else if (param.equals(ATTR_APP_TITLE)) {
      return "The application name is shown in the Play store, as well as in the Manage Applications list in Settings.";
    } else if (param.equals(ATTR_PACKAGE_NAME)) {
      return "The package name must be a unique identifier for your application.\n It is typically not shown to users, " +
             "but it <b>must</b> stay the same for the lifetime of your application; it is how multiple versions of the same application " +
             "are considered the \"same app\".\nThis is typically the reverse domain name of your organization plus one or more " +
             "application identifiers, and it must be a valid Java package name.";
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
    ((NewModuleWizardState)myTemplateState).updateParameters();
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
    if (!super.validate()) {
      return false;
    }

    ((NewModuleWizardState)myTemplateState).updateParameters();

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        boolean updated = false;
        if (myTemplateState.myModified.contains(ATTR_MODULE_NAME)) {
          updated |= updateDerivedValue(ATTR_APP_TITLE, myAppName, new Callable<String>() {
            @Override
            public String call() {
              return computeAppName();
            }
          });
        }
        updated |= updateDerivedValue(ATTR_MODULE_NAME, myModuleName, new Callable<String>() {
          @Override
          public String call() {
            return computeModuleName();
          }
        });
        updated |= updateDerivedValue(ATTR_PACKAGE_NAME, myPackageName, new Callable<String>() {
          @Override
          public String call() {
            return computePackageName();
          }
        });
        if (!myTemplateState.myHidden.contains(ATTR_PROJECT_LOCATION)) {
          updated |= updateDerivedValue(ATTR_PROJECT_LOCATION, myProjectLocation.getTextField(), new Callable<String>() {
            @Override
            public String call() {
              return computeProjectLocation();
            }
          });
        }
        if (updated) {
          validate();
        }
        if (!myInitializedPackageNameText) {
          myInitializedPackageNameText = true;
          if (((String)myTemplateState.get(ATTR_PACKAGE_NAME)).startsWith(SAMPLE_PACKAGE_PREFIX)) {
            int length = SAMPLE_PACKAGE_PREFIX.length();
            if (SAMPLE_PACKAGE_PREFIX.endsWith(".")) {
              length--;
            }
            myPackageName.select(0, length);
          }
        }
      }
    });
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
    if (packageName.startsWith(SAMPLE_PACKAGE_PREFIX)) {
      setErrorHtml(String.format("The prefix '%1$s' is meant as a placeholder and should " +
                                    "not be used", SAMPLE_PACKAGE_PREFIX));
    }

    String moduleName = (String)myTemplateState.get(ATTR_MODULE_NAME);
    if (moduleName == null || moduleName.isEmpty()) {
      setErrorHtml("Please specify a module name.");
      return false;
    } else if (!isValidModuleName(moduleName)) {
      setErrorHtml("Invalid module name.");
      return false;
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

    if (!myTemplateState.myHidden.contains(ATTR_PROJECT_LOCATION)) {
      String projectLocation = (String)myTemplateState.get(ATTR_PROJECT_LOCATION);
      if (projectLocation == null || projectLocation.isEmpty()) {
        setErrorHtml("Please specify a project location");
        return false;
      }
      File file = new File(projectLocation);
      if (file.exists()) {
        setErrorHtml("There must not already be a file or directory at the project location");
        return false;
      }
      if (file.getParent() == null) {
        setErrorHtml("The project location can not be at the filesystem root");
        return false;
      }
      if (file.getParentFile().exists() && !file.getParentFile().isDirectory()) {
        setErrorHtml("The project location's parent directory must be a directory, not a plain file");
        return false;
      }
    }

    return true;
  }

  private boolean updateDerivedValue(@NotNull String attrName, @NotNull JTextField textField, @NotNull Callable<String> valueDeriver) {
    boolean updated = false;
    try {
      myIgnoreUpdates = true;
      if (!myTemplateState.myModified.contains(attrName)) {
        String s = valueDeriver.call();
        if (s != null && !s.equals(myTemplateState.get(attrName))) {
          myTemplateState.put(attrName, s);
          textField.setText(s);
          myTemplateState.myModified.remove(attrName);
          updated = true;
        }
      }
    }
    catch (Exception e) {
    }
    finally {
      myIgnoreUpdates = false;
    }
    return updated;
  }

  @NotNull
  private String computePackageName() {
    String moduleName = (String)myTemplateState.get(ATTR_MODULE_NAME);
    if (moduleName != null && !moduleName.isEmpty()) {
      moduleName = moduleName.replaceAll("[^a-zA-Z0-9_\\-]", "");
      moduleName = moduleName.toLowerCase();
      return SAMPLE_PACKAGE_PREFIX + moduleName;
    } else {
      return "";
    }
  }

  @NotNull
  private String computeModuleName() {
    String name = (String)myTemplateState.get(ATTR_APP_TITLE);
    if (name == null) {
      return "";
    }
    name = name.replaceAll("[^a-zA-Z0-9_\\-.]", "");
    return name;
  }

  private static boolean isValidModuleName(@NotNull String moduleName) {
    if (!moduleName.replaceAll(INVALID_FILENAME_CHARS, "").equals(moduleName)) {
      return false;
    }
    for (String s : Splitter.on('.').split(moduleName)) {
      if (INVALID_MSFT_FILENAMES.contains(s.toLowerCase())) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private String computeAppName() {
    return (String)myTemplateState.get(ATTR_MODULE_NAME);
  }

  @NotNull
  private String computeProjectLocation() {
    return new File(NewProjectWizardState.getProjectFileDirectory(), (String)myTemplateState.get(ATTR_MODULE_NAME) + "Project")
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
