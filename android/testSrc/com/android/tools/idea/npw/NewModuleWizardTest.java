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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.*;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Lists;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashSet;
import icons.AndroidIcons;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

/**
 * Tests for {@link NewModuleWizard}
 */
public class NewModuleWizardTest extends AndroidTestCase {

  @Override
  protected void collectAllowedRoots(final List<String> roots) throws IOException {
    JavaSdk javaSdk = JavaSdk.getInstance();
    final List<String> jdkPaths = Lists.newArrayList(javaSdk.suggestHomePaths());
    jdkPaths.add(SystemProperties.getJavaHome());
    roots.addAll(jdkPaths);

    for (final String jdkPath : jdkPaths) {
      FileUtil.processFilesRecursively(new File(jdkPath), new Processor<File>() {
        @Override
        public boolean process(File file) {
          try {
            String path = file.getCanonicalPath();
            if (!FileUtil.isAncestor(jdkPath, path, false)) {
              roots.add(path);
            }
          }
          catch (IOException ignore) { }
          return true;
        }
      });
    }
  }

  public void testTemplateChanged() throws Exception {
    NewModuleWizard wizard = NewModuleWizard.createNewModuleWizard(myModule.getProject());
    TemplateWizardModuleBuilder moduleBuilder = (TemplateWizardModuleBuilder)wizard.myModuleBuilder;

    moduleBuilder.templateChanged(TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME);
    assertTrue(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LIBRARY_MODULE));
    assertFalse(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LAUNCHER));
    assertFalse(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));

    moduleBuilder.templateChanged(TemplateWizardModuleBuilder.APP_TEMPLATE_NAME);
    assertFalse(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LIBRARY_MODULE));
    assertTrue(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_IS_LAUNCHER));
    assertTrue(moduleBuilder.myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));

    Disposer.dispose(wizard.getDisposable());
  }

  public void testBuildChooseModuleStep() throws Exception {
    File otherTemplateDir = new File(TemplateManager.getTemplateRootFolder(), Template.CATEGORY_PROJECTS);
    List<String> templateDirFiles = Arrays.asList(otherTemplateDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return !name.startsWith(".");
      }
    }));
    int expectedCount = templateDirFiles.size() - 3 + 3 /* Less "ImportExistingProject", "NewAndroidModule" and "NewAndroidProject"
                                                           added project, library, Gradle import */;
    NewModuleWizardPathFactory[] extensions = Extensions.getExtensions(NewModuleWizardPathFactory.EP_NAME);
    for (NewModuleWizardPathFactory factory : extensions) {
      Collection<WizardPath> paths = factory.createWizardPaths(new NewModuleWizardState(), new TemplateWizardStep.UpdateListener() {
        @Override
        public void update() {

        }
      }, getProject(), null, getTestRootDisposable());
      for (WizardPath path : paths) {
        expectedCount += path.getBuiltInTemplates().size();
      }
    }

    ArrayList<ModuleWizardStep> steps = Lists.newArrayList();
    TemplateWizardModuleBuilder myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myModule.getProject(),
                                                                                  AndroidIcons.Wizards.NewModuleSidePanel,
                                                                                  steps, getTestRootDisposable(), false) {
      @Override
      public void update() {
        // Do nothing
      }
    };

    assertInstanceOf(steps.get(0), ChooseTemplateStep.class);
    ChooseTemplateStep chooseTemplateStep = (ChooseTemplateStep)steps.get(0);

    // Make sure we've got an actual object
    assertNotNull(chooseTemplateStep);

    DefaultListModel templateMetadatas = (DefaultListModel)chooseTemplateStep.myTemplateList.getModel();
    Set<String> templateNames = new HashSet<>(templateMetadatas.getSize());
    for (Object o : templateMetadatas.toArray()) {
      templateNames.add(o.toString());
    }

    // Make sure we have the right number of choices
    assertEquals(expectedCount, templateMetadatas.getSize());

    // Ensure we're offering NewApplication and NewLibrary
    assertContainsElements(templateNames, TemplateWizardModuleBuilder.APP_TEMPLATE_NAME, TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME);

    // Ensure we're not offering duplicate elements in the list
    assertEquals(templateNames.size(), templateMetadatas.getSize());
  }

  /**
   * Test what paths the wizard takes depending on the first page selection.
   */
  public void testWizardPaths() {
    NewModuleWizard wizard = NewModuleWizard.createNewModuleWizard(myModule.getProject());

    try {
      // On some systems JDK and Android SDK location might not be known - then the wizard will proceed to a page to set them up
      final Class<?> firstStepNewModuleClass;
      if (IdeSdks.getJdk() != null && IdeSdks.getAndroidSdkPath() != null) {
        // Should proceed to Android module creation first step
        firstStepNewModuleClass = ConfigureAndroidModuleStep.class;
      }
      else {
        // Needs to setup JDK/Android SDK paths
        firstStepNewModuleClass = ChooseAndroidAndJavaSdkStep.class;
      }
      assertInstanceOf(wizard.getCurrentStepObject(), ChooseTemplateStep.class);

      // Import path
      assertFollowingTheRightPath(wizard, NewModuleWizardState.MODULE_IMPORT_NAME, ImportSourceLocationStep.class);
      // New module path
      assertFollowingTheRightPath(wizard, TemplateWizardModuleBuilder.APP_TEMPLATE_NAME, firstStepNewModuleClass);
      // Import archive
      assertFollowingTheRightPath(wizard, NewModuleWizardState.ARCHIVE_IMPORT_NAME, WrapArchiveOptionsStep.class);
    }
    finally {
      Disposer.dispose(wizard.getDisposable());
    }
  }

  private static void assertFollowingTheRightPath(NewModuleWizard wizard, String templateName, Class<?> stepClass) {
    wizard.myModuleBuilder.templateChanged(templateName);
    wizard.doNextAction();
    assertInstanceOf(wizard.getCurrentStepObject(), stepClass);
    wizard.doPreviousAction();
    assertInstanceOf(wizard.getCurrentStepObject(), ChooseTemplateStep.class);
  }
}
