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
package com.android.tools.idea.npw.deprecated;

import com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import org.junit.Test;

import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.COMPANY_DOMAIN_KEY;
import static org.junit.Assert.assertEquals;

public final class ConfigureAndroidProjectStepTest {
  @Test
  public void packageNameDeriverSantizesCompanyDomainKey() {
    ScopedStateStore state = new ScopedStateStore(ScopedStateStore.Scope.WIZARD, null, null);
    ScopedStateStore stepState = new ScopedStateStore(ScopedStateStore.Scope.STEP, state, null);
    stepState.put(APPLICATION_NAME_KEY, "My&App");
    stepState.put(COMPANY_DOMAIN_KEY, "sub.exa-mple.com");

    assertEquals("com.exa_mple.sub.myapp", ConfigureAndroidProjectStep.PACKAGE_NAME_DERIVER.deriveValue(stepState, null, null));

    stepState.put(COMPANY_DOMAIN_KEY, "#.badstartchar.com");
    assertEquals("com.badstartchar.myapp", ConfigureAndroidProjectStep.PACKAGE_NAME_DERIVER.deriveValue(stepState, null, null));

    stepState.put(COMPANY_DOMAIN_KEY, "TEST.ALLCAPS.COM");
    assertEquals("com.allcaps.test.myapp", ConfigureAndroidProjectStep.PACKAGE_NAME_DERIVER.deriveValue(stepState, null, null));
  }

  @Test
  public void nameToPackageReturnsSanitizedPackageName() {
    assertEquals("", ConfigureAndroidProjectStep.nameToPackage("#"));
    assertEquals("aswitch", ConfigureAndroidProjectStep.nameToPackage("switch"));
    assertEquals("myapplication", ConfigureAndroidProjectStep.nameToPackage("#My $AppLICATION"));
    assertEquals("myapplication", ConfigureAndroidProjectStep.nameToPackage("My..\u2603..APPLICATION"));
  }
}
