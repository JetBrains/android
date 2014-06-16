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

import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTagValue;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.FrameworkResourceLoader;

public class ResourceResolverCacheTest extends AndroidTestCase {
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

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
    final PsiFile psiFile3 = PsiManager.getInstance(project).findFile(file3);
    assertNotNull(psiFile3);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    final Configuration configuration1 = configurationManager.getConfiguration(file1);
    Configuration configuration2 = configurationManager.getConfiguration(file2);

    assertNotNull(configuration1.getTheme());
    assertEquals(configuration2.getTheme(), configuration1.getTheme());

    ResourceResolver resolver1 = configuration1.getResourceResolver();
    ResourceResolver resolver2 = configuration2.getResourceResolver();
    assertSame(resolver1, resolver2);
    assertSame(resolver1, configuration1.getResourceResolver());

    configuration1.setTheme("Theme.Light");
    final ResourceResolver resolver1b = configuration1.getResourceResolver();
    assertNotSame(resolver1b, resolver1);
    assertNotSame(resolver1b, resolver2);
    assertSame(resolver1b, configuration1.getResourceResolver());

    configuration2.setTheme("Theme.Light");
    assertSame(resolver1b, configuration2.getResourceResolver());

    // Test project resource changes, should invalidate
    final LocalResourceRepository resources = myFacet.getModuleResources(true);
    assertNotNull(resources); final long generation = resources.getModificationCount();
    assertEquals("Cancel", configuration1.getResourceResolver().findResValue("@string/cancel", false).getValue());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        //noinspection ConstantConditions
        XmlTagValue value = ((XmlFile)psiFile3).getRootTag().getSubTags()[1].getValue();
        assertEquals("Cancel", value.getTrimmedText());
        value.setText("\"FooBar\"");
      }
    });
    assertTrue(resources.isScanPending(psiFile3));
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        assertTrue(generation < resources.getModificationCount());
        assertNotSame(resolver1b, configuration1.getResourceResolver());
        assertEquals("FooBar", configuration1.getResourceResolver().findResValue("@string/cancel", false).getValue());
      }
    });

    ResourceResolverCache cache = configuration1.getConfigurationManager().getResolverCache();
    assertSame(cache, configuration2.getConfigurationManager().getResolverCache());

    ResourceRepository frameworkResources = cache.getFrameworkResources(configuration1.getFullConfig(), configuration1.getTarget());
    assertTrue(frameworkResources instanceof FrameworkResourceLoader.IdeFrameworkResources);
    assertTrue(((FrameworkResourceLoader.IdeFrameworkResources)frameworkResources).getSkippedLocales());
  }
}
