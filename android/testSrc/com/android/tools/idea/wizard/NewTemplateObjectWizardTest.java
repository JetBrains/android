/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for the base class for creating files from templates.
 */
public class NewTemplateObjectWizardTest extends AndroidGradleTestCase {
  private static final Logger LOG = Logger.getInstance(NewTemplateObjectWizard.class);

  private Module myAppModule;
  private Module myLibModule;
  private AndroidFacet myAppFacet;
  private AndroidFacet myLibFacet;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue(CAN_SYNC_PROJECTS);

    loadProject("projects/projectWithAppandLib");
    assertNotNull(myAndroidFacet);
    IdeaAndroidProject gradleProject = myAndroidFacet.getIdeaAndroidProject();
    assertNotNull(gradleProject);

    // Set up modules
    for (Module m : ModuleManager.getInstance(getProject()).getModules()) {
      if (m.getName().equals("lib")) {
        myLibModule = m;
      } else if (m.getName().equals("app")) {
        myAppModule = m;
      }
    }
    assertNotNull(myLibModule);
    assertNotNull(myAppModule);

    myAppFacet = AndroidFacet.getInstance(myAppModule);
    myLibFacet = AndroidFacet.getInstance(myLibModule);

    assertNotNull(myAppFacet);
    assertNotNull(myLibFacet);

    addAndroidSdk(myLibModule, getTestSdkPath(), getPlatformDir());
    addAndroidSdk(myAppModule, getTestSdkPath(), getPlatformDir());

    assertNotNull(AndroidPlatform.getInstance(myAppModule));
    assertNotNull(AndroidPlatform.getInstance(myLibModule));
  }

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public void testInit() throws Exception {
    // Test with invocation target = module directory (so we should default to app package)
    assertNotNull(myAppFacet.getIdeaAndroidProject());
    VirtualFile moduleFile = myAppFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/app");
    assertNotNull(javaSrcFile);



    // Test invocation with null invocation target
    NewTemplateObjectWizard wizard = new NewTemplateObjectWizard(myAppModule.getProject(), myAppModule, null,
                                                                 Template.CATEGORY_OTHER);
    assertNull(wizard.myWizardState.get(ATTR_APPLICATION_PACKAGE));
    assertEquals("com.example.projectwithappandlib.app", wizard.myWizardState.getString(ATTR_PACKAGE_NAME));
    assertFalse(wizard.myWizardState.myHidden.contains(ATTR_PACKAGE_NAME));
    assertFalse(wizard.myWizardState.myFinal.contains(ATTR_PACKAGE_NAME));

    // Test invocation on the module level
    wizard = new NewTemplateObjectWizard(myAppModule.getProject(), myAppModule, moduleFile, Template.CATEGORY_OTHER);
    assertNull(wizard.myWizardState.get(ATTR_APPLICATION_PACKAGE));
    assertEquals("com.example.projectwithappandlib.app", wizard.myWizardState.getString(ATTR_PACKAGE_NAME));

    // If we don't explicitly select a package, we allow the user to edit the package name
    assertFalse(wizard.myWizardState.myHidden.contains(ATTR_PACKAGE_NAME));
    assertFalse(wizard.myWizardState.myFinal.contains(ATTR_PACKAGE_NAME));

    // Test invocation on the app package
    wizard = new NewTemplateObjectWizard(myAppModule.getProject(), myAppModule, javaSrcFile, Template.CATEGORY_OTHER);

    assertNull(wizard.myWizardState.get(ATTR_APPLICATION_PACKAGE));
    assertEquals("com.example.projectwithappandlib.app", wizard.myWizardState.getString(ATTR_PACKAGE_NAME));

    // If we invoke the wizard on a package, we'll use that package and not allow editing
    assertTrue(wizard.myWizardState.myHidden.contains(ATTR_PACKAGE_NAME));
    assertTrue(wizard.myWizardState.myFinal.contains(ATTR_PACKAGE_NAME));

    // Test invocation on the package level
    VirtualFile javaDir = moduleFile.findFileByRelativePath("src/main/java");
    assertNotNull(javaDir);

    // Test non-app package
    File directory = new File(javaDir.getPath(), "com/example/foo");
    assertTrue(directory.mkdirs());
    VirtualFile virtualDirectory = VfsUtil.findFileByIoFile(directory, true);
    assertNotNull(virtualDirectory);

    wizard = new NewTemplateObjectWizard(myAppModule.getProject(), myAppModule, virtualDirectory, Template.CATEGORY_OTHER);

    assertNotNull(wizard.myWizardState.get(ATTR_APPLICATION_PACKAGE));
    assertEquals("com.example.projectwithappandlib.app", wizard.myWizardState.getString(ATTR_APPLICATION_PACKAGE));
    assertEquals("com.example.foo", wizard.myWizardState.getString(ATTR_PACKAGE_NAME));
    assertTrue(wizard.myWizardState.myHidden.contains(ATTR_PACKAGE_NAME));
    assertTrue(wizard.myWizardState.myFinal.contains(ATTR_PACKAGE_NAME));

    // Ensure other parameters have been properly set
    assertNotNull(wizard.myWizardState.get(ATTR_MIN_API));
    assertEquals(myAppFacet.getAndroidModuleInfo().getMinSdkVersion(), wizard.myWizardState.getInt(ATTR_MIN_API_LEVEL));
    assertEquals(false, wizard.myWizardState.getBoolean(ATTR_IS_LIBRARY_MODULE));
    assertEquals("app", wizard.myWizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME));

    // Test library invocation
    assertNotNull(myLibFacet.getIdeaAndroidProject());
    wizard = new NewTemplateObjectWizard(myLibModule.getProject(), myLibModule, myLibFacet.getIdeaAndroidProject().getRootDir(),
                                         Template.CATEGORY_OTHER);
    assertEquals(true, wizard.myWizardState.getBoolean(ATTR_IS_LIBRARY_MODULE));
    assertEquals("lib", wizard.myWizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME));
  }

  public void testTemplateChanged() throws Exception {
    NewTemplateObjectWizard wizard = new NewTemplateObjectWizard(myAppModule.getProject(), myAppModule, null,
                                                                 Template.CATEGORY_OTHER);
    AssetSetStep mockAssetSetStep = mock(AssetSetStep.class);
    wizard.myAssetSetStep = mockAssetSetStep;
    ChooseTemplateStep mockChooseTemplateStep = mock(ChooseTemplateStep.class);
    wizard.myChooseTemplateStep = mockChooseTemplateStep;

    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(Template.CATEGORY_OTHER);
    TemplateMetadata notificationMetadata = null;
    TemplateMetadata fragmentMetadata = null;

    for (File f : templates) {
      if (f.getName().equals("Notification")) {
        notificationMetadata = manager.getTemplate(f);
      } else if (f.getName().equals("BlankFragment")) {
        fragmentMetadata = manager.getTemplate(f);
      }
    }

    assertNotNull(notificationMetadata);
    assertNotNull(fragmentMetadata);

    when(mockChooseTemplateStep.getSelectedTemplateMetadata()).thenReturn(notificationMetadata);
    wizard.templateChanged("Notification");

    verify(mockAssetSetStep).finalizeAssetType(AssetStudioAssetGenerator.AssetType.NOTIFICATION);
    assertNotNull(wizard.myWizardState.get(ATTR_ICON_NAME));
    verify(mockAssetSetStep).setVisible(true);

    when(mockChooseTemplateStep.getSelectedTemplateMetadata()).thenReturn(fragmentMetadata);
    wizard.templateChanged("BlankFragment");
    verify(mockAssetSetStep).setVisible(false);
  }

  public void testFindSrcDirectory() {
    TemplateWizardState wizardState = new TemplateWizardState();
    String expectedPath = "src/main/java";

    // Test with primary module
    assertNotNull(myAppFacet.getIdeaAndroidProject());
    VirtualFile srcDir = NewTemplateObjectWizard.findSrcDirectory(myAppFacet, myAppFacet.getIdeaAndroidProject(), wizardState);

    VirtualFile moduleFile = myAppFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile expectedFile = moduleFile.findFileByRelativePath(expectedPath);
    assertNotNull(expectedFile);

    assertEquals(expectedFile, srcDir);
    assertEquals(expectedPath, wizardState.getString(ATTR_SRC_DIR));

    // Test with lib module
    assertNotNull(myLibFacet.getIdeaAndroidProject());
    srcDir = NewTemplateObjectWizard.findSrcDirectory(myLibFacet, myLibFacet.getIdeaAndroidProject(), wizardState);

    moduleFile = myLibFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    expectedFile = moduleFile.findFileByRelativePath(expectedPath);
    assertNotNull(expectedFile);

    assertEquals(expectedFile, srcDir);
    assertEquals(expectedPath, wizardState.getString(ATTR_SRC_DIR));
  }

  public void testFindResDirectory() {
    TemplateWizardState wizardState = new TemplateWizardState();
    String expectedPath = "src/main/res";

    // Test with primary module
    assertNotNull(myAppFacet.getIdeaAndroidProject());
    VirtualFile resDir = NewTemplateObjectWizard.findResDirectory(myAppFacet, myAppFacet.getIdeaAndroidProject(), wizardState);

    VirtualFile moduleFile = myAppFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile expectedFile = moduleFile.findFileByRelativePath(expectedPath);
    assertNotNull(expectedFile);

    assertEquals(expectedFile, resDir);
    assertEquals(expectedPath, wizardState.getString(ATTR_RES_DIR));

    // Test with lib module
    assertNotNull(myLibFacet.getIdeaAndroidProject());
    resDir = NewTemplateObjectWizard.findResDirectory(myLibFacet, myLibFacet.getIdeaAndroidProject(), wizardState);

    moduleFile = myLibFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    expectedFile = moduleFile.findFileByRelativePath(expectedPath);
    assertNotNull(expectedFile);

    assertEquals(expectedFile, resDir);
    assertEquals(expectedPath, wizardState.getString(ATTR_RES_DIR));
  }

  public void testFindManifestDirectory() {
    TemplateWizardState wizardState = new TemplateWizardState();
    String expectedPath = "src/main";

    // Test with primary module
    assertNotNull(myAppFacet.getIdeaAndroidProject());
    VirtualFile manifestDir = NewTemplateObjectWizard.findManifestDirectory(myAppFacet, myAppFacet.getIdeaAndroidProject(), wizardState);

    VirtualFile moduleFile = myAppFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile expectedFile = moduleFile.findFileByRelativePath(expectedPath);
    assertNotNull(expectedFile);

    assertEquals(expectedFile, manifestDir);
    assertEquals(expectedPath, wizardState.getString(ATTR_MANIFEST_DIR));

    // Test with lib module
    assertNotNull(myLibFacet.getIdeaAndroidProject());
    manifestDir = NewTemplateObjectWizard.findManifestDirectory(myLibFacet, myLibFacet.getIdeaAndroidProject(), wizardState);

    moduleFile = myLibFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    expectedFile = moduleFile.findFileByRelativePath(expectedPath);
    assertNotNull(expectedFile);

    assertEquals(expectedFile, manifestDir);
    assertEquals(expectedPath, wizardState.getString(ATTR_MANIFEST_DIR));
  }

  public void testGetPackageFromDirectory() {
    TemplateWizardState wizardState = new TemplateWizardState();
    wizardState.put(ATTR_PACKAGE_NAME, "wizard.state.default.value");

    assertNotNull(myAppFacet.getIdeaAndroidProject());
    VirtualFile moduleFile = myAppFacet.getIdeaAndroidProject().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile javaDir = moduleFile.findFileByRelativePath("src/main/java");
    assertNotNull(javaDir);

    // Test non-app package
    File directory = new File(javaDir.getPath(), "com/example/foo");
    assertTrue(directory.mkdirs());
    VirtualFile virtualDirectory = VfsUtil.findFileByIoFile(directory, true);
    assertNotNull(virtualDirectory);
    assertEquals("com.example.foo", NewTemplateObjectWizard.getPackageFromDirectory(virtualDirectory, myAppFacet,
                                                                                    myAppFacet.getIdeaAndroidProject(),
                                                                                    myAppModule, wizardState));
    // Test null result on failure
    assertNull(
      NewTemplateObjectWizard.getPackageFromDirectory(moduleFile, myAppFacet, myAppFacet.getIdeaAndroidProject(), myAppModule, wizardState));
  }
}
