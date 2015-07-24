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
package com.android.tools.idea.templates;

import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.w3c.dom.Element;

import javax.imageio.metadata.IIOMetadataNode;

import static com.android.tools.idea.templates.Template.*;
import static org.junit.Assume.assumeTrue;
import static com.android.tools.idea.templates.Parameter.Constraint.UNIQUE;

/**
 * Test for uniqueness and existence for Parameter validation. In fact, these are the same exact tests,
 * since UNIQUE and exists are inverse constraints.
 */
public class UniqueParameterTest extends AndroidGradleTestCase {
  private Module myAppModule;
  private AndroidFacet myAppFacet;
  private SourceProvider myPaidSourceProvider;
  private SourceProvider myMainSourceProvider;
  Parameter myParameter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    assumeTrue(CAN_SYNC_PROJECTS);

    loadProject("projects/projectWithAppandLib");
    assertNotNull(myAndroidFacet);
    IdeaAndroidProject androidModel = myAndroidFacet.getAndroidModel();
    assertNotNull(androidModel);

    // Set up modules
    for (Module m : ModuleManager.getInstance(getProject()).getModules()) {
      if (m.getName().equals("app")) {
        myAppModule = m;
        break;
      }
    }

    assertNotNull(myAppModule);

    myAppFacet = AndroidFacet.getInstance(myAppModule);

    assertNotNull(myAppFacet);

    addAndroidSdk(myAppModule, getTestSdkPath(), getPlatformDir());

    assertNotNull(AndroidPlatform.getInstance(myAppModule));

    assertNotNull(myAppFacet.getAndroidModel());
    ProductFlavorContainer paidFlavor = myAppFacet.getAndroidModel().findProductFlavor("paid");
    assertNotNull(paidFlavor);
    myPaidSourceProvider = paidFlavor.getSourceProvider();
    assertNotNull(myPaidSourceProvider);

    myMainSourceProvider = myAppFacet.getMainSourceProvider();
    assertNotNull(myMainSourceProvider);

    TemplateMetadata mockMetadata = Mockito.mock(TemplateMetadata.class);

    Element elem = new IIOMetadataNode();

    elem.setAttribute(ATTR_TYPE, Parameter.Type.STRING.toString());
    elem.setAttribute(ATTR_ID, "testParam");
    elem.setAttribute(ATTR_DEFAULT, "");
    elem.setAttribute(ATTR_SUGGEST, null);
    elem.setAttribute(ATTR_NAME, "Test Param");
    elem.setAttribute(ATTR_HELP, "This is a test parameter");
    elem.setAttribute(ATTR_CONSTRAINTS, "");

    myParameter = new Parameter(mockMetadata, elem);
  }

  private void assertViolates(@Nullable String packageName, @Nullable SourceProvider provider,
                              @Nullable String value, Parameter.Constraint c) {
    assertTrue(myParameter.validateStringType(getProject(), myAppModule, provider, packageName, value).contains(c));
  }

  private void assertPasses(@Nullable String packageName, @Nullable SourceProvider provider,
                            @Nullable String value, Parameter.Constraint c) {
    assertFalse(myParameter.validateStringType(getProject(), myAppModule, provider, packageName, value).contains(c));
  }

  private void assertViolates(@Nullable String value, Parameter.Constraint c) {
    assertTrue(myParameter.validateStringType(getProject(), myAppModule, null, null, value).contains(c));
  }

  private void assertPasses(@Nullable String value, Parameter.Constraint c) {
    assertFalse(myParameter.validateStringType(getProject(), myAppModule, null, null, value).contains(c));
  }


  public void testUniqueLayout() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.LAYOUT);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myMainSourceProvider, "activity_main", UNIQUE);
    assertViolates(null, myMainSourceProvider, "fragment_main", UNIQUE);

    assertPasses(null, myPaidSourceProvider, "activity_main", UNIQUE);
    assertPasses(null, myPaidSourceProvider, "fragment_main", UNIQUE);

    assertPasses(null, myMainSourceProvider, "blahblahblah", UNIQUE);
  }

  public void testUniqueDrawable() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.DRAWABLE);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myMainSourceProvider, "drawer_shadow", UNIQUE);
    assertViolates(null, myMainSourceProvider, "ic_launcher", UNIQUE);

    assertPasses(null, myPaidSourceProvider, "drawer_shadow", UNIQUE);
    assertPasses(null, myPaidSourceProvider, "ic_launcher", UNIQUE);

    assertPasses(null, myMainSourceProvider, "blahblahblah", UNIQUE);
  }

  public void testUniqueModule() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.MODULE);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, null, "app", UNIQUE);
    assertViolates(null, null, "lib", UNIQUE);

    assertPasses(null, null, "foo", UNIQUE);
  }

  // Existence check is the same for PACKAGE and APP_PACKAGE
  public void testUniquePackage() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.PACKAGE);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates("com.example.projectwithappandlib", UNIQUE);
    assertViolates("com.example.projectwithappandlib.app", UNIQUE);

    // Ensure distinction between source sets
    assertViolates(null, myPaidSourceProvider, "com.example.projectwithappandlib.app.paid", UNIQUE);
    assertPasses(null, myMainSourceProvider, "com.example.projectwithappandlib.app.paid", UNIQUE);

    assertPasses("com.example.foo", UNIQUE);

    assertPasses("org.android.blah", UNIQUE);
  }

  public void testUniqueClass() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.CLASS);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates("com.example.projectwithappandlib.app", myMainSourceProvider, "MainActivity", UNIQUE);
    assertViolates("com.example.projectwithappandlib.app", myMainSourceProvider, "NavigationDrawerFragment", UNIQUE);

    assertViolates("com.example.projectwithappandlib.app.paid", myPaidSourceProvider, "BlankFragment", UNIQUE);

    assertPasses("com.example.foo", myMainSourceProvider, "MainActivity", UNIQUE);

    assertPasses("com.example.projectwithappandlib.app", myMainSourceProvider, "MainActivity2", UNIQUE);
    assertPasses("com.example.projectwithappandlib.app", myPaidSourceProvider, "MainActivity", UNIQUE);
  }

  public void testUniqueLayoutWithLayoutAlias() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.LAYOUT);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myMainSourceProvider, "fragment_foo", UNIQUE);
    assertPasses(null, myPaidSourceProvider, "fragment_foo", UNIQUE);
  }
}
