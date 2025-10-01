/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.adb.AdbOptionsService;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.intellij.testFramework.LightPlatform4TestCase;
import java.io.IOException;
import javax.xml.bind.JAXBException;
import org.junit.Test;

public class ConnectionAssistantTest extends LightPlatform4TestCase {
  @Test
  public void testCreation() throws IOException, JAXBException {
    // Act
    var stream = new ConnectionAssistantBundleCreator().getConfig().openStream();
    TutorialBundleData bundleData =
      DefaultTutorialBundle.parse(stream, ConnectionAssistantBundleCreator.BUNDLE_ID);

    // Assert
    assertThat(bundleData.getName()).isEqualTo("Connection Assistant");
  }

  @Test
  public void testCreationForUnsupportedConnectionAssistant() throws IOException, JAXBException {
    // Prepare
    AdbOptionsService.getInstance().getOptionsUpdater().setUseUserManagedAdb(true).commit();

    // Act
    var stream = new ConnectionAssistantBundleCreator().getConfig().openStream();
    TutorialBundleData bundleData =
      DefaultTutorialBundle.parse(stream, ConnectionAssistantBundleCreator.BUNDLE_ID);

    // Assert
    assertThat(bundleData.getName()).isEqualTo("Connection Assistant");
  }
}
