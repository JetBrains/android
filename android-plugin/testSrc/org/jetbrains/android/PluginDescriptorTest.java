// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

public class PluginDescriptorTest extends LightPlatformTestCase {
  public void testPluginDescription() {
    @Nullable IdeaPluginDescriptor androidPlugin =
      PluginManager.getInstance().findEnabledPlugin(PluginId.getId("org.jetbrains.android"));

    assertNotNull(androidPlugin);
    assertTrue(androidPlugin.getDescription().startsWith("Supports the development of"));
    assertFalse(androidPlugin.getDescription().startsWith("IDE support for running Android Lint"));
  }

  public void testPluginBundle() {
    @Nullable IdeaPluginDescriptor androidPlugin =
      PluginManager.getInstance().findEnabledPlugin(PluginId.getId("org.jetbrains.android"));

    assertNotNull(androidPlugin);
    assertEquals("messages.AndroidBundle", androidPlugin.getResourceBundleBaseName());
    Assert.assertNotEquals("messages.LintBundle", androidPlugin.getResourceBundleBaseName());
  }
}
