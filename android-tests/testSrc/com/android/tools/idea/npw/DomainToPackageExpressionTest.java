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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.properties.expressions.Expression;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DomainToPackageExpressionTest {
  @Test
  public void packageNameDeriverSantizesCompanyDomainKey() {
    StringProperty companyDomain = new StringValueProperty("sub.exa-mple.com");
    StringProperty applicationName = new StringValueProperty("My&App");
    Expression<String> computedPackageName = new DomainToPackageExpression(companyDomain, applicationName);

    assertEquals("com.exa_mple.sub.myapp", computedPackageName.get());

    companyDomain.set("#.badstartchar.com");
    assertEquals("com.badstartchar.myapp", computedPackageName.get());

    companyDomain.set("TEST.ALLCAPS.COM");
    assertEquals("com.allcaps.test.myapp", computedPackageName.get());

    applicationName.set("#My $AppLICATION");
    assertEquals("com.allcaps.test.myapplication", computedPackageName.get());
  }
}
