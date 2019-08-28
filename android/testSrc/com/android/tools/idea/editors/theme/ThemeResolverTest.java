/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.projectsystem.AndroidProjectSystemProvider;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.android.tools.idea.projectsystem.TestRepositories;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ThemeResolverTest extends AndroidTestCase {
  /*
   * The test SDK only includes some resources. It only includes a few incomplete styles.
   */

  public void testFrameworkThemeRead() {
    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    // It's system theme and we're not specifying namespace so it will fail.
    assertNull(themeResolver.getTheme(ResourceReference.style(ResourceNamespace.RES_AUTO, "Theme.Holo.Light")));

    ConfiguredThemeEditorStyle theme = themeResolver.getTheme(ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Holo.Light"));
    assertEquals("Theme.Holo.Light", theme.getName());

    assertEquals(themeResolver.getThemesCount(), themeResolver.getFrameworkThemes().size()); // Only framework themes.
    assertEmpty(themeResolver.getLocalThemes());

    assertNull("Theme resolver shouldn't resolve styles",
               themeResolver.getTheme(ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance")));
  }

  public void testLocalThemes() throws IOException {
    doTestLocalThemes();
  }

  public void testLocalThemesNamespaced() throws IOException {
    enableNamespacing("com.example.app");
    doTestLocalThemes();
  }

  private void doTestLocalThemes() throws IOException {
    VirtualFile layoutFile = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    VirtualFile styleFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(layoutFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    assertEquals(1, themeResolver.getLocalThemes().size()); // There are no libraries, so this will only include the project theme.
    assertEquals(0, themeResolver.getExternalLibraryThemes().size()); // No library themes.

    assertNull("The theme is an app theme and shouldn't be returned for the android namespace",
               themeResolver.getTheme(ResourceReference.style(ResourceNamespace.ANDROID, "Theme.MyTheme")));

    ResourceNamespace moduleNamespace = ResourceRepositoryManager.getOrCreateInstance(myModule).getNamespace();
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme(ResourceReference.style(moduleNamespace, "Theme.MyTheme"));
    assertNotNull(theme);
    assertEquals("Theme.MyTheme", theme.getName());
    assertEquals("Theme", theme.getParent().getName());

    assertEquals(1, theme.getConfiguredValues().size());
    ConfiguredElement<StyleItemResourceValue> value = Iterables.get(theme.getConfiguredValues(), 0);
    assertEquals("windowBackground", value.getElement().getAttr().getName());
    assertEquals("@drawable/pic", value.getElement().getValue());

    // Modify a value.
    theme.setValue("android:windowBackground", "@drawable/other");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertFalse(new String(styleFile.contentsToByteArray(), UTF_8).contains("@drawable/pic"));
    assertTrue(new String(styleFile.contentsToByteArray(), UTF_8).contains("@drawable/other"));

    // Add a value.
    theme.setValue("android:windowBackground2", "@drawable/second_background");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertTrue(new String(styleFile.contentsToByteArray(), UTF_8).contains("@drawable/other"));
    assertTrue(new String(styleFile.contentsToByteArray(), UTF_8).contains("@drawable/second_background"));
  }

  /** Check that, after a configuration update, the resolver updates the list of themes */
  public void testConfigurationUpdate() {
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    VirtualFile file = myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(file);
    ResourceNamespace moduleNamespace = ResourceRepositoryManager.getOrCreateInstance(myModule).getNamespace();

    ThemeEditorContext context = new ThemeEditorContext(configuration);
    ThemeResolver resolver = context.getThemeResolver();
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V20OnlyTheme")));
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V19OnlyTheme")));
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V17OnlyTheme")));

    // Set API level 17 and check that only the V17 theme can be resolved.
    //noinspection ConstantConditions
    configuration.setTarget(new CompatibilityRenderTarget(configurationManager.getHighestApiTarget(), 17, null));
    context = new ThemeEditorContext(configuration);
    resolver = context.getThemeResolver();
    assertNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V20OnlyTheme")));
    assertNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V19OnlyTheme")));
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V17OnlyTheme")));
  }

  /**
   * Regression test for b/111857682. Checks that we can handle LocalResourceRepository.EmptyRepository as the module resources.
   */
  public void testEmptyModuleResources() throws IOException {
    WriteAction.run(() -> myFixture.getTempDirFixture().getFile("res").delete(this));
    Configuration configuration = Configuration.create(ConfigurationManager.getOrCreateInstance(myModule),
                                                       null,
                                                       FolderConfiguration.createDefault());
    new ThemeResolver(configuration);
  }

  public void testRequiredBaseThemesWithNoDesignLibraryPresent() {
    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    assertThat(themeResolver.requiredBaseThemes()).isEmpty();
  }

  public void testRequiredBaseThemesWithDesignLibraryPresent() {
    TestProjectSystem projectSystem = new TestProjectSystem(getProject(), TestRepositories.PLATFORM_SUPPORT_LIBS);
    ExtensionPointName<AndroidProjectSystemProvider> epName = new ExtensionPointName<>("com.android.project.projectsystem");
    ServiceContainerUtil.registerExtension(getProject(), epName, projectSystem, getTestRootDisposable());
    projectSystem.addDependency(GoogleMavenArtifactId.APP_COMPAT_V7, myModule, new GradleVersion(1337, 600613));
    projectSystem.addDependency(GoogleMavenArtifactId.DESIGN, myModule, new GradleVersion(1338, 600614));

    myFixture.addFileToProject("res/values/values.xml", "<resources>\n" +
                                                        "    <style name=\"Platform.AppCompat\" parent=\"Theme.Material\"/>\n" +
                                                        "    <style name=\"Platform.AppCompat.Light\" parent=\"Theme.Material.Light\"/>\n" +
                                                        "</resources>\n");

    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layoutFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    assertThat(Arrays.stream(themeResolver.requiredBaseThemes()).map(style -> style.getName()).collect(Collectors.toList()))
      .containsExactly("Platform.AppCompat", "Platform.AppCompat.Light");
  }

  public void testRecommendedThemesNoDependencies() {
    VirtualFile layoutFile = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(layoutFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    assertThat(themeResolver.getRecommendedThemes()).containsExactly(
        ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Material.Light.NoActionBar"),
        ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Material.NoActionBar"));
  }

  public void testRecommendedThemesAppcompat() {
    doTestRecommendedThemesAppcompat();
  }

  public void testRecommendedThemesAppcompatNamespaced() {
    enableNamespacing("com.example.app");
    doTestRecommendedThemesAppcompat();
  }

  private void doTestRecommendedThemesAppcompat() {
    TestProjectSystem projectSystem = new TestProjectSystem(getProject(), TestRepositories.PLATFORM_SUPPORT_LIBS);
    ExtensionPointName<AndroidProjectSystemProvider> epName = new ExtensionPointName<>("com.android.project.projectsystem");
    ServiceContainerUtil.registerExtension(getProject(), epName, projectSystem, getTestRootDisposable());
    projectSystem.addDependency(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7, myModule, new GradleVersion(1337, 600613));

    ResourceNamespace appcompatNamespace =
        ResourceRepositoryManager.getOrCreateInstance(myModule).getNamespacing() == AaptOptions.Namespacing.DISABLED
        ? ResourceNamespace.RES_AUTO
        : ResourceNamespace.APPCOMPAT;
    VirtualFile layoutFile = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(layoutFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    assertThat(themeResolver.getRecommendedThemes()).containsExactly(
      ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Material.Light.NoActionBar"),
      ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Material.NoActionBar"),
      ResourceReference.style(appcompatNamespace, "Theme.AppCompat.Light.NoActionBar"),
      ResourceReference.style(appcompatNamespace, "Theme.AppCompat.NoActionBar"));
  }
}
