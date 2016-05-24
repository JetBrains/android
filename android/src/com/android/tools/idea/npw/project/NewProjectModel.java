/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

public class NewProjectModel extends WizardModel {
  private static final String PROPERTIES_DOMAIN_KEY = "SAVED_COMPANY_DOMAIN";
  private static final String EXAMPLE_DOMAIN = "example.com";
  private static final Pattern DISALLOWED_IN_DOMAIN = Pattern.compile("[^a-zA-Z]*");

  private final StringProperty myApplicationName = new StringValueProperty("My Application");
  private final StringProperty myCompanyDomain = new StringValueProperty(getInitialDomain());
  private final StringProperty myPackageName = new StringValueProperty();
  private final StringProperty myProjectLocation = new StringValueProperty();
  private final BoolProperty myEnableCppSupport = new BoolValueProperty(false);

  public NewProjectModel() {
    // Save entered company domain
    myCompanyDomain.addListener(sender -> {
      String domain = myCompanyDomain.get();
      if (AndroidUtils.isValidAndroidPackageName(domain)) {
        PropertiesComponent.getInstance().setValue(PROPERTIES_DOMAIN_KEY, domain);
      }
    });
  }

  /**
   * Loads saved company domain, or generates a dummy one if no domain has been saved
   */
  @NotNull
  private static String getInitialDomain() {
    String domain = PropertiesComponent.getInstance().getValue(PROPERTIES_DOMAIN_KEY);
    if (domain != null) {
      return domain;
    }

    String userName = System.getProperty("user.name");
    return userName == null ? EXAMPLE_DOMAIN : toPackagePart(userName) + '.' + EXAMPLE_DOMAIN;
  }

  public StringProperty packageName() {
    return myPackageName;
  }

  public StringProperty applicationName() {
    return myApplicationName;
  }

  public StringProperty companyDomain() {
    return myCompanyDomain;
  }

  public StringProperty projectLocation() {
    return myProjectLocation;
  }

  public BoolProperty enableCppSupport() {
    return myEnableCppSupport;
  }

  @NotNull
  public static String toPackagePart(@NotNull String s) {
    return DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("").toLowerCase(Locale.US);
  }

  @NotNull
  public static String sanitizeApplicationName(@NotNull String s) {
    return DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("");
  }

  @Override
  protected void handleFinished() {
    // TODO: Fill this out with logic from ConfigureAndroidProjectPath
  }
}
