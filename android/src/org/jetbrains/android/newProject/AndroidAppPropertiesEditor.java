// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.newProject;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAppPropertiesEditor {
  private JTextField myApplicationNameField;
  private JTextField myPackageNameField;
  private JCheckBox myHelloAndroidCheckBox;
  private JPanel myActivtiyPanel;
  private JTextField myActivityNameField;
  private JLabel myErrorLabel;
  private JPanel myContentPanel;

  private final ModulesProvider myModulesProvider;
  private boolean myApp;
  private boolean myPackageNameFieldChangedByUser;

  public AndroidAppPropertiesEditor(String moduleName, ModulesProvider modulesProvider) {
    myModulesProvider = modulesProvider;

    String defaultAppName = moduleName != null ? moduleName : "myapp";
    myApplicationNameField.setText(defaultAppName);
    myApplicationNameField.selectAll();
    myPackageNameField.setText(getDefaultPackageNameByModuleName(defaultAppName));

    myHelloAndroidCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateActivityPanel();
      }
    });
    myPackageNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myPackageNameFieldChangedByUser = true;
        String message = validatePackageName(!myApp);
        myErrorLabel.setText(message);
      }
    });
    myActivityNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        String message = validateActivityName();
        myErrorLabel.setText(message);
      }
    });
    myApplicationNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (!myPackageNameFieldChangedByUser) {
          updatePackageNameField();
          myPackageNameFieldChangedByUser = false;
        }
      }
    });
  }

  private void updatePackageNameField() {
    final String appName = myApplicationNameField.getText().trim();

    if (!appName.isEmpty()) {
      myPackageNameField.setText(getDefaultPackageNameByModuleName(appName));
    }
  }

  public void update(boolean app) {
    myApplicationNameField.setEnabled(app);
    myHelloAndroidCheckBox.setEnabled(app);
    if (app) {
      updateActivityPanel();
    }
    else {
      UIUtil.setEnabled(myActivtiyPanel, app, true);
    }
    myApp = app;
    final String message = validatePackageName(!app);
    myErrorLabel.setText(message);
  }

  @NotNull
  public static String getDefaultPackageNameByModuleName(@NotNull String moduleName) {
    return "com.example." + toIdentifier(moduleName);
  }

  @NotNull
  private static String toIdentifier(@NotNull String s) {
    final StringBuilder result = new StringBuilder();

    for (int i = 0, n = s.length(); i < n; i++) {
      final char c = s.charAt(i);

      if (Character.isJavaIdentifierPart(c)) {
        if (i == 0 && !Character.isJavaIdentifierStart(c)) {
          result.append('_');
        }
        result.append(c);
      }
      else {
        result.append('_');
      }
    }
    return result.toString();
  }

  public void updateActivityPanel() {
    myErrorLabel.setForeground(JBColor.RED);
    UIUtil.setEnabled(myActivtiyPanel, myHelloAndroidCheckBox.isSelected(), true);
  }

  private String validatePackageName(boolean library) {
    final String candidate = myPackageNameField.getText().trim();
    return doValidatePackageName(library, candidate, myModulesProvider);
  }

  @NotNull
  static String doValidatePackageName(boolean library, @NotNull String candidate, @Nullable ModulesProvider modulesProvider) {
    if (candidate.isEmpty()) {
      return AndroidBundle.message("specify.package.name.error");
    }
    if (!AndroidUtils.isValidAndroidPackageName(candidate)) {
      return candidate;
    }
    if (!AndroidBuildCommonUtils.contains2Identifiers(candidate)) {
      return AndroidBundle.message("package.name.must.contain.2.ids.error");
    }

    if (!library) {
      for (Module module : modulesProvider.getModules()) {
        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null && !facet.getConfiguration().isLibraryProject()) {
          final Manifest manifest = Manifest.getMainManifest(facet);
          if (manifest != null) {
            final String packageName = manifest.getPackage().getValue();
            if (candidate.equals(packageName)) {
              return "Package name '" + packageName + "' is already used by module '" + module.getName() + "'";
            }
          }
        }
      }
    }
    return "";
  }

  private String validateActivityName() {
    String candidate = myActivityNameField.getText().trim();
    if (!AndroidUtils.isIdentifier(candidate)) {
      return AndroidBundle.message("not.valid.activity.name.error", candidate);
    }
    return "";
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  public void validate(boolean library) throws ConfigurationException {
    String message = validatePackageName(library);
    if (!message.isEmpty()) {
      throw new ConfigurationException(message);
    }
    if (!library) {
      message = validateActivityName();
      if (!message.isEmpty()) {
        throw new ConfigurationException(message);
      }
    }
  }

  public String getActivityName() {
    return myHelloAndroidCheckBox.isSelected() ? myActivityNameField.getText().trim() : "";
  }

  public String getPackageName() {
    return myPackageNameField.getText().trim();
  }

  public String getApplicationName() {
    return myApplicationNameField.getText().trim();
  }

  public JTextField getApplicationNameField() {
    return myApplicationNameField;
  }

  public JTextField getPackageNameField() {
    return myPackageNameField;
  }
}
