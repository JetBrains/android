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

package com.android.tools.idea.structure;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.utils.NullLogger;
import com.google.common.collect.Lists;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.io.FileUtil.resolveShortWindowsName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;

/**
 * {@linkplain AndroidHomeConfigurable} is a Project Structure {@link Configurable} that lets the user set global Android SDK and JDK
 * locations that are used for new projects or Gradle projects lacking a local.properties file. It does this by managing a set of IntelliJ
 * SDK entities with the appropriate home directories set. It is intended that this SDK management be done behind the scenes and these SDKs
 * not revealed to the user.
 * <p>
 * Since IntelliJ expects different build targets from the same Android SDK to be treated as different SDKs, this class will explode a
 * single Android SDK home location into a set of IntelliJ SDKs depending on the build targets it has installed.
 */
public class AndroidHomeConfigurable implements Configurable {
  private static final Logger LOG = Logger.getInstance(AndroidHomeConfigurable.class);

  private static final Pattern SDK_NAME_PATTERN = Pattern.compile(".*\\(\\d+\\)");
  private String myOriginalJdkHome;
  private String myOriginalSdkHome;
  private FieldPanel myAndroidHomeLocation;
  private FieldPanel myJavaHomeLocation;
  private JPanel myWholePanel;
  private JLabel myAndroidHomeError;
  private JLabel myJavaHomeError;
  private DetailsComponent myDetailsComponent;
  private JavaSdkImpl myJavaSdk = new JavaSdkImpl();

  public AndroidHomeConfigurable() {
    myWholePanel.setPreferredSize(new Dimension(700,500));

    myAndroidHomeLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        boolean androidHomeValid = validateAndroidHome();
        myAndroidHomeLocation.getTextField().setForeground(
            androidHomeValid ? UIUtil.getFieldForegroundColor() : PathEditor.INVALID_COLOR);
        myAndroidHomeError.setText(androidHomeValid ? " " : "Please choose a valid Android SDK directory.");
      }
    });

    myJavaHomeLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        boolean javaHomeValid = validateJavaHome();
        myJavaHomeLocation.getTextField().setForeground(
            javaHomeValid ? UIUtil.getFieldForegroundColor() : PathEditor.INVALID_COLOR);
        myJavaHomeError.setText(javaHomeValid ? " " : "Please choose a valid JDK directory.");
      }
    });

    myAndroidHomeError.setForeground(PathEditor.INVALID_COLOR);
    myJavaHomeError.setForeground(PathEditor.INVALID_COLOR);

    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myWholePanel);
    myDetailsComponent.setText("Android SDK location");

    myOriginalSdkHome = getDefaultAndroidHome();
    myOriginalJdkHome = getDefaultJavaHome();
  }

  @Override
  public void disposeUIResources() {
  }

  @Override
  public void reset() {
    myAndroidHomeLocation.setText(myOriginalSdkHome);
    myJavaHomeLocation.setText(myOriginalJdkHome);
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (validateAndroidHome()) {
          String path = resolvePath(myAndroidHomeLocation.getText());
          for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
            if (sdk.getName().startsWith(AndroidSdkUtils.SDK_NAME_PREFIX)) {
              setSdkPath(sdk, path);
            }
          }
          // Fill in any APIs that didn't already exist.
          createSdksForAllTargets(path);
          updateLocalProperties(path);
        }
        if (validateJavaHome()) {
          String canonicalPath = resolvePath(myJavaHomeLocation.getText());
          Sdk jdk = getDefaultJdk();
          if (jdk != null) {
            setSdkPath(jdk, canonicalPath);
          }
          else {
            VirtualFile path = LocalFileSystem.getInstance().findFileByPath(canonicalPath);
            if (path != null) createJdk(path);
          }
        }
        deleteExtraSdks();
      }
    });
  }

  private static void setSdkPath(Sdk sdk, String path) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(path);
    sdkModificator.removeAllRoots();
    sdkModificator.commitChanges();
  }

  private void createUIComponents() {
    JTextField textField = new JTextField();
    FileChooserDescriptor outputPathsChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    BrowseFilesListener listener = new BrowseFilesListener(textField, "", "Please choose an Android SDK location",
                                                           outputPathsChooserDescriptor);
    myAndroidHomeLocation = new FieldPanel(textField, null, null, listener, EmptyRunnable.getInstance());
    FileChooserFactory fileChooserFactory = FileChooserFactory.getInstance();
    fileChooserFactory.installFileCompletion(myAndroidHomeLocation.getTextField(), outputPathsChooserDescriptor, true, null);

    textField = new JTextField();
    outputPathsChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    listener = new BrowseFilesListener(textField, "", "Please choose a JDK location",
                                       outputPathsChooserDescriptor);
    myJavaHomeLocation = new FieldPanel(textField, null, null, listener, EmptyRunnable.getInstance());
    fileChooserFactory.installFileCompletion(myJavaHomeLocation.getTextField(), outputPathsChooserDescriptor, true, null);
  }

  @Override
  public String getDisplayName() {
    return "Android SDK";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }


  @Nullable
  @Override
  public JComponent createComponent() {
    return myDetailsComponent.getComponent();
  }

  @Override
  public boolean isModified() {
    return
        !toSystemIndependentName(urlToPath(myOriginalSdkHome)).equals(toSystemIndependentName(myAndroidHomeLocation.getText())) ||
        !toSystemIndependentName(urlToPath(myOriginalJdkHome)).equals(toSystemIndependentName(myJavaHomeLocation.getText()));
  }

  /**
   * Takes an OS-dependent path and normalizes it into generic format.
   */
  @NotNull
  private static String resolvePath(@NotNull String path) {
    try {
      path = resolveShortWindowsName(path);
    }
    catch (IOException e) {
      //file doesn't exist yet
    }
    path = toSystemIndependentName(path);
    return path;
  }

  /**
   * Filters through all Android SDKs and returns only those that have our special name prefix and which have additional data and a
   * platform. They should all have those values, but this provides extra NullPointerException protection.
   */
  @NotNull
  private static Collection<Pair<Sdk, AndroidSdkAdditionalData>> getEligibleAndroidSdks() {
    List<Pair<Sdk, AndroidSdkAdditionalData>> list = Lists.newArrayList();
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (sdk.getName().startsWith(AndroidSdkUtils.SDK_NAME_PREFIX) && sdkAdditionalData != null &&
          sdkAdditionalData.getAndroidPlatform() != null) {
        list.add(new Pair<Sdk, AndroidSdkAdditionalData>(sdk, sdkAdditionalData));
      }
    }
    return list;
  }

  /**
   * Returns the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   * target installed in the SDK; which of those this method returns is not defined.
   *
   * @param create True if this method should attempt to create an SDK if one does not exist.
   * @return null if an SDK is unavailable or creation failed.
   */
  @Nullable
  private static Sdk getFirstDefaultAndroidSdk(boolean create) {
    Collection<Pair<Sdk, AndroidSdkAdditionalData>> allAndroidSdks = getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.iterator().next().first;
    }
    if (!create) return null;
    SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkManager == null) return null;
    String location = sdkManager.getLocation();
    List<Sdk> sdks = createSdksForAllTargets(location);
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  /**
   * Creates a set of IntelliJ SDKs (one for each build target) corresponding to the Android SDK in the given directory, if SDKs with the
   * default naming convention and each individual build target do not already exist. If IntelliJ SDKs do exist, they are not updated.
   */
  @NotNull
  public static List<Sdk> createSdksForAllTargets(@NotNull String homeDirectory) {
    if (!homeDirectory.endsWith(File.separator)) {
      homeDirectory += File.separator;
    }
    List<Sdk> sdks = Lists.newArrayList();
    AndroidSdkData sdkData = AndroidSdkData.parse(homeDirectory, NullLogger.getLogger());
    if (sdkData != null) {
      IAndroidTarget[] targets = sdkData.getTargets();
      Sdk defaultJdk = getDefaultJdk();
      for (IAndroidTarget target : targets) {
        if (target.isPlatform() && !doesDefaultAndroidSdkExist(target)) {
          sdks.add(AndroidSdkUtils.createNewAndroidPlatform(target, homeDirectory, AndroidSdkUtils.chooseNameForNewLibrary(target),
                                                            defaultJdk, true));
        }
      }
    }
    return sdks;
  }

  private static void deleteExtraSdks() {
    List<Sdk> sdksToDelete = Lists.newArrayList();
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      String name = sdk.getName();
      if (!name.startsWith(AndroidSdkUtils.SDK_NAME_PREFIX)|| SDK_NAME_PATTERN.matcher(name).matches()) {
        sdksToDelete.add(sdk);
      }
    }
    for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())) {
      String name = sdk.getName();
      if (!name.equals(AndroidSdkUtils.DEFAULT_JDK_NAME) || SDK_NAME_PATTERN.matcher(name).matches()) {
        sdksToDelete.add(sdk);
      }
    }
    for (Sdk sdk : sdksToDelete) {
      SdkConfigurationUtil.removeSdk(sdk);
    }
  }

  private static void updateLocalProperties(String path) {
    ProjectManager projectManager = ApplicationManager.getApplication().getComponent(ProjectManager.class);
    for (Project project : projectManager.getOpenProjects()) {
      try {
        LocalProperties localProperties = new LocalProperties(project);
        localProperties.setAndroidSdkPath(path);
        localProperties.save();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  /**
   * Returns what the IDE is using as the home path for the Android SDK for new projects.
   */
  public static String getDefaultAndroidHome() {
    String sdkHome = null;
    Sdk sdk = getFirstDefaultAndroidSdk(true);
    if (sdk != null) sdkHome = sdk.getHomePath();
    if (sdkHome == null) sdkHome = "";
    return sdkHome;
  }

  /**
   * Returns what the IDE is using as the home path for the JDK.
   */
  public static String getDefaultJavaHome() {
    String jdkHome = null;
    for (Pair<Sdk, AndroidSdkAdditionalData> pair : getEligibleAndroidSdks()) {
      Sdk jdk = pair.second.getJavaSdk();
      if (jdk != null) {
        jdkHome = jdk.getHomePath();
        if (jdkHome != null) break;
      }
    }
    if (jdkHome == null) jdkHome = "";
    return jdkHome;
  }

  /**
   * Returns true if an IntelliJ SDK with the default naming convention already exists for the given Android build target.
   */
  private static boolean doesDefaultAndroidSdkExist(@NotNull IAndroidTarget target) {
    for (Pair<Sdk, AndroidSdkAdditionalData> pair : getEligibleAndroidSdks()) {
      //noinspection ConstantConditions
      if (pair.second.getAndroidPlatform().getTarget().getVersion().equals(target.getVersion())) return true;
    }
    return false;
  }

  /**
   * Returns true if {@link #myAndroidHomeLocation} points to a valid Android SDK.
   */
  private boolean validateAndroidHome() {
    return AndroidSdkType.validateAndroidSdk(myAndroidHomeLocation.getText()).first;
  }

  /**
   * Returns true if {@link #myJavaHomeLocation} points to a valid JDK.
   * @return
   */
  private boolean validateJavaHome() {
    return myJavaSdk.isValidSdkHome(myJavaHomeLocation.getText());
  }

  /**
   * Creates an IntelliJ SDK for the JDK at the given location and returns it, or null if it could not be created successfully.
   */
  @Nullable
  private static Sdk createJdk(VirtualFile homeDirectory) {
    Sdk newSdk = SdkConfigurationUtil.setupSdk(ProjectJdkTable.getInstance().getAllJdks(), homeDirectory, JavaSdk.getInstance(), true, null,
                                               AndroidSdkUtils.DEFAULT_JDK_NAME);
    if (newSdk != null) {
      SdkConfigurationUtil.addSdk(newSdk);
    }
    return newSdk;
  }

  /**
   * Returns the JDK with the default naming convention, creating one if it is not set up. Returns the SDK or null if it could not be
   * created.
   */
  @Nullable
  private static Sdk getDefaultJdk() {
    Sdk sdk = getFirstDefaultAndroidSdk(false);
    if (sdk == null) {
      List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
      if (!jdks.isEmpty()) {
        return jdks.get(0);
      }
      VirtualFile javaHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getJavaHome());
      if (javaHome != null) return createJdk(javaHome);
      return null;
    }
    AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (sdkAdditionalData != null) {
      return sdkAdditionalData.getJavaSdk();
    } else {
      return null;
    }
  }
}
