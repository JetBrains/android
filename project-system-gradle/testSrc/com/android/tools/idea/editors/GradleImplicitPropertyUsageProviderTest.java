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
package com.android.tools.idea.editors;

import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTestCase;

public class GradleImplicitPropertyUsageProviderTest extends AndroidTestCase {
  public void testGradleWrapper() {
    Project project = getProject();
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(new GradleProjectSystem(project));
    VirtualFile vFile = myFixture.copyFileToProject("projects/projectWithAppandLib/gradle/wrapper/gradle-wrapper.properties",
                                                    "wrapper/gradle-wrapper.properties");
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    assertNotNull(file);
    PropertiesFile propertiesFile = (PropertiesFile)file;
    GradleImplicitPropertyUsageProvider provider = new GradleImplicitPropertyUsageProvider();
    for (IProperty property : propertiesFile.getProperties()) {
      // All properties are considered used in this file
      String name = property.getName();
      assertTrue(name, provider.isUsed((Property)property));
    }
  }

  public void testLocalProperties() {
    Project project = getProject();
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(new GradleProjectSystem(project));
    VirtualFile vFile = myFixture.copyFileToProject("test.properties", "local.properties");
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    assertNotNull(file);
    PropertiesFile propertiesFile = (PropertiesFile)file;
    GradleImplicitPropertyUsageProvider provider = new GradleImplicitPropertyUsageProvider();
    for (IProperty property : propertiesFile.getProperties()) {
      Property p = (Property)property;
      // Only but the property with "unused" in its name are considered used
      String name = property.getName();
      if (name.contains("unused")) {
        assertFalse(name, provider.isUsed(p));
      } else {
        assertTrue(name, provider.isUsed(p));
      }
    }
  }

  // Regression test for b/298540715
  public void testResourcesProperties() {
    Project project = getProject();
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(new GradleProjectSystem(project));
    VirtualFile vFile = myFixture.createFile("resources.properties", "unqualifiedResLocale=en-US");
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    assertNotNull(file);
    PropertiesFile propertiesFile = (PropertiesFile) file;
    GradleImplicitPropertyUsageProvider provider = new GradleImplicitPropertyUsageProvider();
    IProperty unqualifiedResLocale = propertiesFile.getProperties().get(0);
    String name = unqualifiedResLocale.getName();
    assertTrue(name, provider.isUsed((Property) unqualifiedResLocale));
  }

  public void testNothingUsedInNonGradleProject() {
    Map<String, String> sourceToTargetMap = Arrays.stream(new Object[][]{
      {"projects/projectWithAppandLib/gradle/wrapper/gradle-wrapper.properties", "wrapper/gradle-wrapper.properties"},
      {"test.properties", "local.properties"}
    }).collect(Collectors.toMap((o) -> (String)o[0], (o) -> (String)o[1]));
    for (Map.Entry<String, String> e : sourceToTargetMap.entrySet()) {
      assertNothingUsedInFile(myFixture.copyFileToProject(e.getKey(), e.getValue()));
    }
    assertNothingUsedInFile(myFixture.createFile("resources.properties", "unqualifiedResLocale=en-US"));
  }

  private void assertNothingUsedInFile(VirtualFile vFile) {
    GradleImplicitPropertyUsageProvider provider = new GradleImplicitPropertyUsageProvider();
    PsiFile file = PsiManager.getInstance(getProject()).findFile(vFile);
    assertNotNull(file);
    PropertiesFile propertiesFile = (PropertiesFile)file;
    List<IProperty> properties = propertiesFile.getProperties();
    assertNotEmpty(properties);
    for (IProperty property : propertiesFile.getProperties()) {
      assertFalse(property.getName(), provider.isUsed((Property)property));
    }
  }
}