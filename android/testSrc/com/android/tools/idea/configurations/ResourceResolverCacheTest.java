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
package com.android.tools.idea.configurations;

import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.google.common.collect.Iterables;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTagValue;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;

public class ResourceResolverCacheTest extends AndroidTestCase {

  public void test() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject("render/layout1.xml", "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject("render/layout2.xml", "res/layout/layout2.xml");
    VirtualFile file3 = myFixture.copyFileToProject("javadoc/strings/strings.xml", "res/values/strings.xml");
    assertNotNull(file1);
    assertNotNull(file2);
    assertNotNull(file3);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    Project project = getProject();
    PsiFile psiFile1 = PsiManager.getInstance(project).findFile(file1);
    assertNotNull(psiFile1);
    PsiFile psiFile2 = PsiManager.getInstance(project).findFile(file2);
    assertNotNull(psiFile2);
    PsiFile psiFile3 = PsiManager.getInstance(project).findFile(file3);
    assertNotNull(psiFile3);
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(configurationManager);
    Configuration configuration1 = configurationManager.getConfiguration(file1);
    Configuration configuration2 = configurationManager.getConfiguration(file2);

    assertNotNull(configuration1.getTheme());
    assertEquals(configuration2.getTheme(), configuration1.getTheme());

    ResourceResolver resolver1 = configuration1.getResourceResolver();
    ResourceResolver resolver2 = configuration2.getResourceResolver();
    assertSame(resolver1, resolver2);
    assertSame(resolver1, configuration1.getResourceResolver());

    configuration1.setTheme("Theme.Light");
    ResourceResolver resolver1b = configuration1.getResourceResolver();
    assertNotSame(resolver1b, resolver1);
    assertNotSame(resolver1b, resolver2);
    assertSame(resolver1b, configuration1.getResourceResolver());

    configuration2.setTheme("Theme.Light");
    assertSame(resolver1b, configuration2.getResourceResolver());

    // Test project resource changes, should invalidate
    LocalResourceRepository resources = StudioResourceRepositoryManager.getModuleResources(myFacet);
    assertNotNull(resources);
    assertEquals("Cancel", configuration1.getResourceResolver().findResValue("@string/cancel", false).getValue());
    long generation = resources.getModificationCount();
    int rescans = resources.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      //noinspection ConstantConditions
      XmlTagValue value = ((XmlFile)psiFile3).getRootTag().getSubTags()[1].getValue();
      assertEquals("Cancel", value.getTrimmedText());
      value.setText("\"FooBar\"");
    });
    waitForUpdates(resources);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans + 1);
    assertNotSame(resolver1b, configuration1.getResourceResolver());
    assertEquals("FooBar", configuration1.getResourceResolver().findResValue("@string/cancel", false).getValue());

    ResourceResolverCache cache = configuration1.getConfigurationManager().getResolverCache();
    assertSame(cache, configuration2.getConfigurationManager().getResolverCache());
  }

  public void testCustomConfiguration() {
    VirtualFile file1 = myFixture.copyFileToProject("render/layout1.xml", "res/layout/layout1.xml");
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(file1);
    ResourceResolverCache cache = configurationManager.getResolverCache();

    assertTrue(cache.myResolverMap.isEmpty());
    assertTrue(cache.myAppResourceMap.isEmpty());
    assertTrue(cache.myFrameworkResourceMap.isEmpty());

    ResourceResolver resolver = configuration.getResourceResolver();

    assertEquals(1, cache.myResolverMap.size());
    assertEquals(1, cache.myAppResourceMap.size());
    assertEquals(1, cache.myFrameworkResourceMap.size());
    String originalResolverMapKey = Iterables.getFirst(cache.myResolverMap.keySet(), null);
    String originalResourceMapKey = Iterables.getFirst(cache.myAppResourceMap.keySet(), null);
    // Framework and App resource maps use the same key
    assertEquals(originalResourceMapKey, Iterables.getFirst(cache.myFrameworkResourceMap.keySet(), null));

    Device original = configuration.getDevice();
    Device.Builder builder = new Device.Builder(original);
    builder.setName("Custom");
    builder.setId(Configuration.CUSTOM_DEVICE_ID);
    Device customDevice = builder.build();
    customDevice.getAllStates().forEach(state -> {
      Screen screen = state.getHardware().getScreen();
      screen.setXDimension(100);
      screen.setYDimension(100);
    });
    configuration.setEffectiveDevice(customDevice, customDevice.getState("Portrait"));
    ResourceResolver newResolver = configuration.getResourceResolver();

    // The original config should be there plus we've added the custom one
    assertEquals(2, cache.myResolverMap.size());
    assertEquals(2, cache.myAppResourceMap.size());
    assertEquals(2, cache.myFrameworkResourceMap.size());
    assertContainsElements(cache.myResolverMap.keySet(), originalResolverMapKey);
    assertContainsElements(cache.myAppResourceMap.keySet(), originalResourceMapKey);
    assertContainsElements(cache.myFrameworkResourceMap.keySet(), originalResourceMapKey);

    // Get the custom key used for this device
    String customResolverMapKey = cache.myResolverMap.keySet().stream()
      .filter(k -> !k.equals(originalResolverMapKey))
      .findFirst()
      .get();
    String customResourceMapKey = cache.myAppResourceMap.keySet().stream()
      .filter(k -> !k.equals(originalResourceMapKey))
      .findFirst()
      .get();

    assertNotSame(resolver, newResolver);
    assertSame(newResolver, configuration.getResourceResolver());
    resolver = newResolver;

    // No new configuration created
    assertEquals(2, cache.myResolverMap.size());

    configuration.setTheme("android:Theme.Material");
    newResolver = configuration.getResourceResolver();
    assertNotSame(resolver, newResolver);

    // This should replace only the custom config
    assertEquals(2, cache.myResolverMap.size());
    assertEquals(2, cache.myAppResourceMap.size());
    assertEquals(2, cache.myFrameworkResourceMap.size());
    assertContainsElements(cache.myResolverMap.keySet(), originalResolverMapKey);
    assertContainsElements(cache.myAppResourceMap.keySet(), originalResourceMapKey);
    assertContainsElements(cache.myFrameworkResourceMap.keySet(), originalResourceMapKey);
    // We've only changed the theme so the resource maps won't change. They are indexed per device config.
    assertDoesntContain(cache.myResolverMap.keySet(), customResolverMapKey);
  }
}
