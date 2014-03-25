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

import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.InsertPathAction;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;
import java.util.List;

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
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Void, ConfigurationException>() {
      @Override
      public Void compute() throws ConfigurationException {
        try {
          File androidHomeLocation = getAndroidHomeLocation();
          DefaultSdks.setDefaultAndroidHome(androidHomeLocation);
        } catch (IllegalStateException e) {
          throw new ConfigurationException(e.getMessage());
        }
        DefaultSdks.setDefaultJavaHome(getJavaHomeLocation());

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          RunAndroidSdkManagerAction.updateInWelcomePage(myDetailsComponent.getComponent());
        }
        return null;
      }
    });
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
    AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (sdkData == null) return null;
    List<Sdk> sdks = DefaultSdks.createAndroidSdksForAllTargets(sdkData.getLocation());
    return !sdks.isEmpty() ? sdks.get(0) : null;
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
      String sdkHome = sdk.getHomePath();
      if (sdkHome != null) {
        return FileUtil.toSystemDependentName(sdkHome);
      }
    }
    return "";
  }

  /**
   * @return what the IDE is using as the home path for the JDK.
   */
  @NotNull
  private static String getDefaultJavaHomePath() {
    File javaHome = DefaultSdks.getDefaultJavaHome();
    return javaHome != null ? javaHome.getPath() : "";
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
