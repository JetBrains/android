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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.w3c.dom.Element;

import javax.imageio.metadata.IIOMetadataNode;
import java.util.Set;

import static com.android.tools.idea.templates.Parameter.Constraint.UNIQUE;
import static com.android.tools.idea.templates.Template.*;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;

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

    loadProject(PROJECT_WITH_APPAND_LIB);
    assertNotNull(myAndroidFacet);
    AndroidModel androidModel = AndroidModuleModel.get(myAndroidFacet);
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

    Sdk sdk = addLatestAndroidSdk(myAppModule);
    Disposer.register(myAppFacet, ()-> WriteAction.run(()-> ProjectJdkTable.getInstance().removeJdk(sdk)));

    assertNotNull(AndroidPlatform.getInstance(myAppModule));

    assertNotNull(myAppFacet.getAndroidModel());
    // TODO: b/23032990
    ProductFlavorContainer paidFlavor = AndroidModuleModel.get(myAppFacet).findProductFlavor("paid");
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
                              @Nullable String value, Parameter.Constraint c, Set<Object> relatedValues) {
    assertTrue(myParameter.validateStringType(getProject(), myAppModule, provider, packageName, value, relatedValues).contains(c));
  }

  private void assertPasses(@Nullable String packageName, @Nullable SourceProvider provider,
                            @Nullable String value, Parameter.Constraint c, Set<Object> relatedValues) {
    assertFalse(myParameter.validateStringType(getProject(), myAppModule, provider, packageName, value, relatedValues).contains(c));
  }

  private void assertViolates(@Nullable String value, Parameter.Constraint c, Set<Object> relatedValues) {
    assertTrue(myParameter.validateStringType(getProject(), myAppModule, null, null, value, relatedValues).contains(c));
  }

  private void assertPasses(@Nullable String value, Parameter.Constraint c, Set<Object> relatedValues) {
    assertFalse(myParameter.validateStringType(getProject(), myAppModule, null, null, value, relatedValues).contains(c));
  }

  public void testUniqueLayout() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.LAYOUT);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myMainSourceProvider, "activity_main", UNIQUE, null);
    assertViolates(null, myMainSourceProvider, "fragment_main", UNIQUE, null);

    assertPasses(null, myPaidSourceProvider, "activity_main", UNIQUE, null);
    assertPasses(null, myPaidSourceProvider, "fragment_main", UNIQUE, null);

    assertPasses(null, myMainSourceProvider, "blahblahblah", UNIQUE, null);
  }

  public void testUniqueDrawable() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.DRAWABLE);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myMainSourceProvider, "drawer_shadow", UNIQUE, null);
    assertViolates(null, myMainSourceProvider, "ic_launcher", UNIQUE, null);

    assertPasses(null, myPaidSourceProvider, "drawer_shadow", UNIQUE, null);
    assertPasses(null, myPaidSourceProvider, "ic_launcher", UNIQUE, null);

    assertPasses(null, myMainSourceProvider, "blahblahblah", UNIQUE, null);
  }

  public void testUniqueModule() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.MODULE);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, null, "app", UNIQUE, null);
    assertViolates(null, null, "lib", UNIQUE, null);

    assertPasses(null, null, "foo", UNIQUE, null);
  }

  // Existence check is the same for PACKAGE and APP_PACKAGE
  public void testUniquePackage() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.PACKAGE);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates("com.example.projectwithappandlib", UNIQUE, null);
    assertViolates("com.example.projectwithappandlib.app", UNIQUE, null);

    // Ensure distinction between source sets
    assertViolates(null, myPaidSourceProvider, "com.example.projectwithappandlib.app.paid", UNIQUE, null);
    assertPasses(null, myMainSourceProvider, "com.example.projectwithappandlib.app.paid", UNIQUE, null);

    assertPasses("com.example.foo", UNIQUE, null);

    assertPasses("org.android.blah", UNIQUE, null);
  }

  public void testUniqueClass() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.CLASS);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates("com.example.projectwithappandlib.app", myMainSourceProvider, "MainActivity", UNIQUE, null);
    assertViolates("com.example.projectwithappandlib.app", myMainSourceProvider, "NavigationDrawerFragment", UNIQUE, null);

    assertViolates("com.example.projectwithappandlib.app.paid", myPaidSourceProvider, "BlankFragment", UNIQUE, null);

    assertPasses("com.example.foo", myMainSourceProvider, "MainActivity", UNIQUE, null);

    assertPasses("com.example.projectwithappandlib.app", myMainSourceProvider, "MainActivity2", UNIQUE, null);
    assertPasses("com.example.projectwithappandlib.app", myPaidSourceProvider, "MainActivity", UNIQUE, null);

    assertViolates("com.example.projectwithappandlib.app", myMainSourceProvider, "dummy", UNIQUE, null);
    assertPasses("com.example.projectwithappandlib.app", myMainSourceProvider, "dummy2", UNIQUE, null);
  }

  public void testUniqueLayoutWithLayoutAlias() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.LAYOUT);
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myMainSourceProvider, "fragment_foo", UNIQUE, null);
    assertPasses(null, myPaidSourceProvider, "fragment_foo", UNIQUE, null);
  }

  public void testRelatedValue() throws Exception {
    myParameter.constraints.add(Parameter.Constraint.UNIQUE);

    assertViolates(null, myPaidSourceProvider, "fragment_foo", UNIQUE, ImmutableSet.<Object>of("bar", "fragment_foo"));
    assertPasses(null, myPaidSourceProvider, "fragment_foo", UNIQUE, ImmutableSet.<Object>of("bar", "fragment_bar"));

    myParameter.constraints.remove(Parameter.Constraint.UNIQUE);
    assertPasses(null, myPaidSourceProvider, "fragment_foo", UNIQUE, ImmutableSet.<Object>of("bar", "fragment_foo"));
  }
}
