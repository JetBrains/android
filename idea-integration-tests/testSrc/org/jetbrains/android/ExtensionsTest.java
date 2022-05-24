// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android;

import com.android.tools.idea.project.AndroidRunConfigurations;
import com.intellij.ExtensionPoints;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.List;
import java.util.stream.Collectors;

public class ExtensionsTest extends LightPlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    AndroidRunConfigurations inst = ApplicationManager.getApplication().getService(AndroidRunConfigurations.class);
    assertNotNull("Seems that android-plugin.xml has not been loaded", inst);
  }

  public void testErrorReportSubmitterNotInstalled() {
    List<ErrorReportSubmitter> androidEPs = ExtensionPoints.ERROR_HANDLER_EP.extensions()
      .filter(e -> e.toString().contains("android"))
      .collect(Collectors.toList());

    assertTrue("registered EPs: " + androidEPs, androidEPs.isEmpty());
  }
}
