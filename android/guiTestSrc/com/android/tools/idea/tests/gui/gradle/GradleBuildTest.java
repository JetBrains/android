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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.fixture.SelectSdkDialogFixture;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class GradleBuildTest extends GuiTestCase {
  @Test
  public void testBuildWithInvalidJavaHome() throws IOException {
    String jdkPathValue = System.getProperty("jdk.path");
    assumeTrue("jdk.path system property missing", !isEmpty(jdkPathValue));

    File jdkPath = new File(jdkPathValue);

    importSimpleApplication();
    getIdeFrame().invokeProjectMakeAndSimulateFailure("Supplied javaHome is not a valid folder.");

    // Find message dialog explaining the source of the error.
    getIdeFrame().findMessageDialog("Gradle Running").clickOk();

    // Find the dialog to select the path of the JDK.
    SelectSdkDialogFixture selectSdkDialog = SelectSdkDialogFixture.find(robot());
    selectSdkDialog.setJdkPath(jdkPath)
                   .clickOk();

    File actualJdkPath = IdeSdks.getJdkPath();
    assertNotNull(actualJdkPath);
    assertEquals(jdkPath.getPath(), actualJdkPath.getPath());
  }
}
