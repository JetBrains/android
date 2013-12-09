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

import com.android.sdklib.SdkManager;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.google.common.collect.Lists;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.resolveShortWindowsName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;

/**
 * {@linkplain AndroidHomeConfigurable} is a Project Structure {@link Configurable} that lets the user set global Android SDK and JDK
 * locations that are used for new projects or Gradle projects lacking a local.properties file. It does this by managing a set of IntelliJ
 * SDK entities with the appropriate home directories set. It is intended that this SDK management be done behind the scenes and these SDKs
 * not revealed to the user.
 * <p/>
 * Since IntelliJ expects different build targets from the same Android SDK to be treated as different SDKs, this class will explode a
 * single Android SDK home location into a set of IntelliJ SDKs depending on the build targets it has installed.
 */
public class AndroidHomeConfigurable implements Configurable {
  // These paths are system-dependent.
  @NotNull private String myOriginalJdkHomePath;
  @NotNull private String myOriginalSdkHomePath;

  private FieldPanel myAndroidHomeLocation;
  private FieldPanel myJavaHomeLocation;
  private JPanel myWholePanel;
  private JLabel myAndroidHomeError;
  private JLabel myJavaHomeError;
  private DetailsComponent myDetailsComponent;

  public AndroidHomeConfigurable() {
    myWholePanel.setPreferredSize(new Dimension(700, 500));

    myAndroidHomeLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        boolean androidHomeValid = DefaultSdks.validateAndroidSdkPath(getAndroidHomeLocation());
        setBackground(myAndroidHomeLocation, androidHomeValid);
        myAndroidHomeError.setText(androidHomeValid ? " " : "Please choose a valid Android SDK directory.");
      }
    });

    myJavaHomeLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        boolean javaHomeValid = JavaSdk.checkForJdk(getJavaHomeLocation());
        setBackground(myJavaHomeLocation, javaHomeValid);
        myJavaHomeError.setText(javaHomeValid ? " " : "Please choose a valid JDK directory.");
      }
    });

    myAndroidHomeError.setForeground(PathEditor.INVALID_COLOR);
    myJavaHomeError.setForeground(PathEditor.INVALID_COLOR);

    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myWholePanel);
    myDetailsComponent.setText("Android SDK location");

    myOriginalSdkHomePath = getDefaultAndroidHomePath();
    myOriginalJdkHomePath = getDefaultJavaHomePath();
  }

  private static void setBackground(@NotNull FieldPanel fieldPanel, boolean validValue) {
    fieldPanel.getTextField().setForeground(validValue ? UIUtil.getFieldForegroundColor() : PathEditor.INVALID_COLOR);
  }

  @Override
  public void disposeUIResources() {
  }

  @Override
  public void reset() {
    myAndroidHomeLocation.setText(myOriginalSdkHomePath);
    myJavaHomeLocation.setText(myOriginalJdkHomePath);
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        DefaultSdks.setDefaultAndroidHome(getAndroidHomeLocation());

        // Set up a list of SDKs we don't need any more. At the end we'll delete them.
        List<Sdk> sdksToDelete = Lists.newArrayList();

        File javaHomeLocation = getJavaHomeLocation();
        if (JavaSdk.checkForJdk(javaHomeLocation)) {
          String canonicalPath = resolvePath(javaHomeLocation.getPath());
          // Try to set this path into the "default" JDK associated with the IntelliJ SDKs.
          Sdk defaultJdk = DefaultSdks.getDefaultJdk();
          if (defaultJdk != null) {
            setJdkPath(defaultJdk, canonicalPath);

            // Flip through the IntelliJ SDKs and make sure they point to this JDK.
            updateAllSdks(defaultJdk);
          }
          else {
            // We didn't have a JDK set at all. Try to create one.
            VirtualFile path = LocalFileSystem.getInstance().findFileByPath(canonicalPath);
            if (path != null) {
              defaultJdk = DefaultSdks.createJdk(path);
            }
          }

          // Now iterate through all the JDKs and delete any that aren't the default one.
          List<Sdk> jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance());
          if (defaultJdk != null) {
            for (Sdk jdk : jdks) {
              if (jdk.getName() != defaultJdk.getName()) {
                sdksToDelete.add(defaultJdk);
              }
              else {
                // This may actually be a different copy of the SDK than what we obtained from the JDK. Set its path to be sure.
                setJdkPath(jdk, canonicalPath);
              }
            }
          }
        }
        for (final Sdk sdk : sdksToDelete) {
          ProjectJdkTable.getInstance().removeJdk(sdk);
        }
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          RunAndroidSdkManagerAction.updateInWelcomePage(myDetailsComponent.getComponent());
        }
      }
    });
  }

  /**
   * Sets the given JDK's home path to the given path, and resets all of its content roots.
   */
  private static void setJdkPath(@NotNull Sdk sdk, @NotNull String path) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(path);
    sdkModificator.removeAllRoots();
    ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
    sdkModificator.commitChanges();
    JavaSdk.getInstance().setupSdkPaths(sdk);
  }

  private void createUIComponents() {
    JTextField textField = new JTextField();
    FileChooserDescriptor outputPathsChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    BrowseFilesListener listener =
      new BrowseFilesListener(textField, "", "Please choose an Android SDK location", outputPathsChooserDescriptor);
    //noinspection ConstantConditions
    myAndroidHomeLocation = new FieldPanel(textField, null, null, listener, EmptyRunnable.getInstance());
    FileChooserFactory fileChooserFactory = FileChooserFactory.getInstance();
    fileChooserFactory.installFileCompletion(myAndroidHomeLocation.getTextField(), outputPathsChooserDescriptor, true, null);

    textField = new JTextField();
    outputPathsChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    InsertPathAction.addTo(textField, outputPathsChooserDescriptor);
    listener = new BrowseFilesListener(textField, "", "Please choose a JDK location", outputPathsChooserDescriptor);
    //noinspection ConstantConditions
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

  @NotNull
  public JComponent getContentPanel() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    return !myOriginalSdkHomePath.equals(getAndroidHomeLocation().getPath()) ||
           !myOriginalJdkHomePath.equals(getJavaHomeLocation().getPath());
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
   * Returns the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   * target installed in the SDK; which of those this method returns is not defined.
   *
   * @param create True if this method should attempt to create an SDK if one does not exist.
   * @return null if an SDK is unavailable or creation failed.
   */
  @Nullable
  private static Sdk getFirstDefaultAndroidSdk(boolean create) {
    List<Sdk> allAndroidSdks = DefaultSdks.getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    if (!create) return null;
    SdkManager sdkManager = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkManager == null) return null;
    File location = new File(sdkManager.getLocation());
    List<Sdk> sdks = DefaultSdks.createAndroidSdksForAllTargets(location);
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  /**
   * Iterates through all Android SDKs and makes them point to the given JDK.
   */
  private static void updateAllSdks(@NotNull Sdk jdk) {
    for (Sdk sdk : AndroidSdkUtils.getAllAndroidSdks()) {
      AndroidSdkAdditionalData oldData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (oldData == null) {
        continue;
      }
      oldData.setJavaSdk(jdk);
      SdkModificator modificator = sdk.getSdkModificator();
      modificator.setSdkAdditionalData(oldData);
      modificator.commitChanges();
    }
  }

  /**
   * @return what the IDE is using as the home path for the Android SDK for new projects.
   */
  @NotNull
  private static String getDefaultAndroidHomePath() {
    File ideAndroidSdkHomePath = DefaultSdks.getDefaultAndroidHome();
    if (ideAndroidSdkHomePath != null) {
      return ideAndroidSdkHomePath.getPath();
    }
    Sdk sdk = getFirstDefaultAndroidSdk(true);
    if (sdk != null) {
      File sdkHome = getHomePathOf(sdk);
      if (sdkHome != null) {
        return sdkHome.getPath();
      }
    }
    return "";
  }

  /**
   * @return what the IDE is using as the home path for the JDK.
   */
  @NotNull
  private static String getDefaultJavaHomePath() {
    List<Sdk> androidSdks = DefaultSdks.getEligibleAndroidSdks();
    if (androidSdks.isEmpty()) {
      // This happens when user has a fresh installation of Android Studio without an Android SDK, but with a JDK. Android Studio should
      // populate the text field with the existing JDK.
      Sdk jdk = Jdks.chooseOrCreateJavaSdk();
      if (jdk != null) {
        File jdkHome = getHomePathOf(jdk);
        if (jdkHome != null) {
          return jdkHome.getPath();
        }
      }
    }
    else {
      for (Sdk sdk : androidSdks) {
        AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
        assert data != null;
        Sdk jdk = data.getJavaSdk();
        if (jdk != null) {
          File jdkHome = getHomePathOf(jdk);
          if (jdkHome != null) {
            return jdkHome.getPath();
          }
        }
      }
    }
    return "";
  }

  @Nullable
  private static File getHomePathOf(@NotNull Sdk sdk) {
    String homePath = sdk.getHomePath();
    if (homePath != null) {
      return new File(FileUtil.toSystemDependentName(homePath));
    }
    return null;
  }

  @NotNull
  private File getJavaHomeLocation() {
    String javaHome = myJavaHomeLocation.getText();
    return new File(toSystemDependentName(javaHome));
  }

  @NotNull
  private File getAndroidHomeLocation() {
    String androidHome = myAndroidHomeLocation.getText();
    return new File(toSystemDependentName(androidHome));
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return myAndroidHomeLocation.getTextField();
  }

  public boolean validate() throws ConfigurationException {
    File javaHomeLocation = getJavaHomeLocation();
    if (!JavaSdk.checkForJdk(javaHomeLocation)) {
      throw new ConfigurationException("Please choose a valid JDK directory.");
    }

    boolean androidHomeValid = DefaultSdks.validateAndroidSdkPath(getAndroidHomeLocation());
    if (!androidHomeValid) {
      throw new ConfigurationException("Please choose a valid Android SDK directory.");
    }

    return true;
  }

  /**
   * Returns true if the configurable is needed: e.g. if we're missing a JDK or an Android SDK setting
   */
  public static boolean isNeeded() {
    String jdkPath = getDefaultJavaHomePath();
    String sdkPath = getDefaultAndroidHomePath();
    boolean validJdk = !jdkPath.isEmpty() && JavaSdk.checkForJdk(new File(jdkPath));
    boolean validSdk = !sdkPath.isEmpty() && DefaultSdks.validateAndroidSdkPath(new File(sdkPath));
    return !validJdk || !validSdk;
  }
}
