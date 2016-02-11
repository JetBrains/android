/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture.Kind;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.actions.as.CreateFileFromTemplateDialog.Visibility;
import com.intellij.ide.actions.as.CreateNewClassDialogValidatorExImpl;
import org.fest.swing.exception.ComponentLookupException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(GuiTestRunner.class)
public class CreateNewClassDialogGuiTest {
  private static final String PROVIDED_ACTIVITY = "app/src/main/java/google/simpleapplication/MyActivity.java";
  private static final String THING_NAME = "TestThing";
  private static final String PACKAGE_NAME_0 = "google.simpleapplication";
  private static final String PACKAGE_NAME_1 = PACKAGE_NAME_0 + ".sub.pkg";
  private static final String PACKAGE_NAME_2 = PACKAGE_NAME_0 + ".other.pkg";
  private static final String INVALID_PACKAGE_NAME = PACKAGE_NAME_0 + ".";
  private static final String THING_FILE_PATH_0 = "app/src/main/java/google/simpleapplication/TestThing.java";
  private static final String THING_FILE_PATH_1 = "app/src/main/java/google/simpleapplication/sub/pkg/TestThing.java";
  private static final String THING_FILE_PATH_2 = "app/src/main/java/google/simpleapplication/other/pkg/TestThing.java";
  private static final String PUBLIC_DECLARATION = "public %s TestThing {";
  private static final String PACKAGE_PRIVATE_DECLARATION = "%s TestThing {";
  private static final String SUPERCLASS_DECLARATION = "public %s TestThing extends Super0 {";
  private static final String SUPERCLASS_AND_INTERFACE_DECLARATION = "public %s TestThing extends Super0 implements Interface0 {";
  private static final String CLASS_IMPLEMENTING_ONE_INTERFACE_DECLARATION = "public %s TestThing implements Interface0 {";
  private static final String CLASS_IMPLEMENTING_TWO_INTERFACES_DECLARATION = "public %s TestThing implements Interface0, Interface1 {";
  private static final String INTERFACE_EXTENDING_ONE_INTERFACE_DECLARATION = "public %s TestThing extends Interface0 {";
  private static final String INTERFACE_EXTENDING_TWO_INTERFACES_DECLARATION = "public %s TestThing extends Interface0, Interface1 {";
  private static final String SUPERCLASS_0 = "Super0";
  private static final String INVALID_NAME = "Invalid-Class Name";
  private static final ImmutableList<String> ONE_INTERFACE = ImmutableList.of("Interface0");
  private static final ImmutableList<String> TWO_INTERFACES = ImmutableList.of("Interface0", "Interface1");
  private static final ImmutableList<String> INVALID_INTERFACE = ImmutableList.of("Bad-Interface-Name");

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ExpectedException thrown = ExpectedException.none();
  private EditorFixture myEditor;

  @Before
  public void setUp() throws IOException {
    guiTest.importSimpleApplication();
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(PROVIDED_ACTIVITY);
  }

  private CreateFileFromTemplateDialogFixture invokeDialog() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    return CreateFileFromTemplateDialogFixture.find(guiTest.robot());
  }

  private void assertPackageName(String filePath, String packageName) {
    myEditor.open(filePath);
    myEditor.moveTo(0);
    String expectedPackage = "package " + packageName + ";";
    String actualPackage = myEditor.getCurrentLineContents(true, false, 0);
    assertEquals(expectedPackage, actualPackage);
  }

  private void assertDeclaration(String filePath, String expectedDeclaration, Kind kind) {
    myEditor.open(filePath);
    myEditor.moveTo(myEditor.findOffset(kind + " " + THING_NAME + "|"));
    String declarationLine = myEditor.getCurrentLineContents(true, false, 0);
    assertEquals(String.format(expectedDeclaration, kind), declarationLine);
  }

  private void createPackagePrivate(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PACKAGE_PRIVATE);
    dialog.clickOk();
    myEditor.open(THING_FILE_PATH_0);

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, PACKAGE_PRIVATE_DECLARATION, kind);
  }

  private void createWithOneInterface(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setInterfaces(ONE_INTERFACE);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    String declaration = kind.equals(Kind.INTERFACE) || kind.equals(Kind.ANNOTATION)
                         ? INTERFACE_EXTENDING_ONE_INTERFACE_DECLARATION
                         : CLASS_IMPLEMENTING_ONE_INTERFACE_DECLARATION;

    assertDeclaration(THING_FILE_PATH_0, declaration, kind);
  }

  private void createWithTwoInterfaces(Kind kind) throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(kind);
    dialog.setInterfaces(TWO_INTERFACES);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    String declaration = kind.equals(Kind.INTERFACE) || kind.equals(Kind.ANNOTATION)
                         ? INTERFACE_EXTENDING_TWO_INTERFACES_DECLARATION
                         : CLASS_IMPLEMENTING_TWO_INTERFACES_DECLARATION;
    assertDeclaration(THING_FILE_PATH_0, declaration, kind);
  }

  // Create in package tests.
  @Test
  public void createClassInCurrentPackage() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, PUBLIC_DECLARATION, Kind.CLASS);
  }

  @Test
  public void createClassInNewSubPackage() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setPackage(PACKAGE_NAME_1);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_1, PACKAGE_NAME_1);
    assertDeclaration(THING_FILE_PATH_1, PUBLIC_DECLARATION, Kind.CLASS);
  }

  @Test
  public void createClassInNewParallelPackage() throws IOException {
    CreateFileFromTemplateDialogFixture dialog1 = invokeDialog();
    dialog1.setName(THING_NAME);
    dialog1.selectKind(Kind.CLASS);
    dialog1.setPackage(PACKAGE_NAME_1);
    dialog1.setVisibility(Visibility.PUBLIC);
    dialog1.clickOk();
    myEditor.open(THING_FILE_PATH_1);

    CreateFileFromTemplateDialogFixture dialog2 = invokeDialog();
    dialog2.setName(THING_NAME);
    dialog2.selectKind(Kind.CLASS);
    dialog2.setPackage(PACKAGE_NAME_2);
    dialog2.setVisibility(Visibility.PUBLIC);
    dialog2.clickOk();

    assertPackageName(THING_FILE_PATH_2, PACKAGE_NAME_2);
    assertDeclaration(THING_FILE_PATH_2, PUBLIC_DECLARATION, Kind.CLASS);
  }

  @Test
  public void createInParentPackage() throws IOException {
    CreateFileFromTemplateDialogFixture dialog1 = invokeDialog();
    dialog1.setName(THING_NAME);
    dialog1.selectKind(Kind.CLASS);
    dialog1.setPackage(PACKAGE_NAME_1);
    dialog1.setVisibility(Visibility.PUBLIC);
    dialog1.clickOk();
    myEditor.open(THING_FILE_PATH_1);

    CreateFileFromTemplateDialogFixture dialog0 = invokeDialog();
    dialog0.setName(THING_NAME);
    dialog0.selectKind(Kind.CLASS);
    dialog0.setPackage(PACKAGE_NAME_0);
    dialog0.setVisibility(Visibility.PUBLIC);
    dialog0.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, PUBLIC_DECLARATION, Kind.CLASS);
  }

  // New class file template tests.
  @Test
  public void createClassPackagePrivate() throws IOException {
    createPackagePrivate(Kind.CLASS);
  }

  @Test
  public void createClassWithOneInterface() throws IOException {
    createWithOneInterface(Kind.CLASS);
  }

  @Test
  public void createClassWithTwoInterfaces() throws IOException {
    createWithTwoInterfaces(Kind.CLASS);
  }

  @Test
  public void createClassWithSuperclass() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setSuperclass(SUPERCLASS_0);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, SUPERCLASS_DECLARATION, Kind.CLASS);
  }

  @Test
  public void createClassWithSuperclassAndInterface() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setSuperclass(SUPERCLASS_0);
    dialog.setInterfaces(ONE_INTERFACE);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();

    assertPackageName(THING_FILE_PATH_0, PACKAGE_NAME_0);
    assertDeclaration(THING_FILE_PATH_0, SUPERCLASS_AND_INTERFACE_DECLARATION, Kind.CLASS);
  }

  // New enum file template tests.
  @Test
  public void createEnumPackagePrivate() throws IOException {
    createPackagePrivate(Kind.ENUM);
  }

  @Test
  public void createEnumWithOneInterface() throws IOException {
    createWithOneInterface(Kind.ENUM);
  }

  @Test
  public void createEnumWithTwoInterfaces() throws IOException {
    createWithTwoInterfaces(Kind.ENUM);
  }

  // New interface file template tests.
  @Test
  public void createInterfacePackagePrivate() throws IOException {
    createPackagePrivate(Kind.INTERFACE);
  }

  @Test
  public void createInterfaceWithOneInterface() throws IOException {
    createWithOneInterface(Kind.INTERFACE);
  }

  @Test
  public void createInterfaceWithTwoInterfaces() throws IOException {
    createWithTwoInterfaces(Kind.INTERFACE);
  }

  // Invalid field entries tests. These tests ensure the UI reacts appropriately to invalid input. They do not test
  // that all forms of invalid input produce errors. NewClassDialogOptionsValidatorTest does that.
  @Test
  public void invalidName() throws IOException, InterruptedException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(INVALID_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();
    dialog.waitForErrorMessageToAppear();
    String actualMessage = dialog.getMessage();
    String expectedMessage = CreateNewClassDialogValidatorExImpl.INVALID_QUALIFIED_NAME;
    assertEquals(expectedMessage, actualMessage.replaceAll("\n", " "));
    dialog.clickCancel();
  }

  @Test
  public void invalidSuperclass() throws IOException, InterruptedException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setSuperclass(INVALID_NAME);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();
    dialog.waitForErrorMessageToAppear();
    String actualMessage = dialog.getMessage();
    String expectedMessage = CreateNewClassDialogValidatorExImpl.INVALID_QUALIFIED_NAME;
    assertEquals(expectedMessage, actualMessage.replaceAll("\n", " "));
    dialog.clickCancel();
  }

  @Test
  public void invalidInterfaceName() throws IOException, InterruptedException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setInterfaces(INVALID_INTERFACE);
    dialog.setPackage(PACKAGE_NAME_0);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();
    dialog.waitForErrorMessageToAppear();
    String actualMessage = dialog.getMessage();
    String expectedMessage = CreateNewClassDialogValidatorExImpl.INVALID_INTERFACES_MESSAGE.replaceAll("&gt;", ">").replaceAll("&lt;", "<");
    assertEquals(expectedMessage, actualMessage.replaceAll("\n", " "));
    dialog.clickCancel();
  }

  @Test
  public void invalidPackage() throws IOException, InterruptedException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.setName(THING_NAME);
    dialog.selectKind(Kind.CLASS);
    dialog.setPackage(INVALID_PACKAGE_NAME);
    dialog.setVisibility(Visibility.PUBLIC);
    dialog.clickOk();
    dialog.waitForErrorMessageToAppear();
    String actualMessage = dialog.getMessage();
    assertEquals(CreateNewClassDialogValidatorExImpl.INVALID_PACKAGE_MESSAGE, actualMessage.replaceAll("\n", " "));
    dialog.clickCancel();
  }

  // (Un)hiding fields tests.
  @Test
  public void hidingComponents() throws IOException {
    CreateFileFromTemplateDialogFixture dialog = invokeDialog();
    dialog.selectKind(Kind.CLASS);

    assertTrue(dialog.getAbstractCheckBox().isVisible());
    assertTrue(dialog.getFinalCheckBox().isVisible());
    assertTrue(dialog.getOverridesCheckBox().isVisible());

    dialog.selectKind(Kind.INTERFACE);
    thrown.expect(ComponentLookupException.class);
    assertFalse(dialog.getAbstractCheckBox().isVisible());
    thrown.expect(ComponentLookupException.class);
    assertFalse(dialog.getFinalCheckBox().isVisible());
    thrown.expect(ComponentLookupException.class);
    assertFalse(dialog.getOverridesCheckBox().isVisible());

    dialog.selectKind(Kind.CLASS);
    assertTrue(dialog.getAbstractCheckBox().isVisible());
    assertTrue(dialog.getFinalCheckBox().isVisible());
    assertTrue(dialog.getOverridesCheckBox().isVisible());

    dialog.clickCancel();
  }
}
