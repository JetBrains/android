/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.DeviceParser;
import com.android.tools.idea.downloads.DownloadService;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

// TODO Add the thread comments and annotations
@Service
public final class DeviceDefinitionDownloadService extends DownloadService {
  private DeviceDefinitionDownloadService() {
    super("device definitions",
          StudioFlags.DEVICE_DEFINITION_DOWNLOAD_SERVICE_URL.get(),
          Objects.requireNonNull(DeviceDefinitionDownloadService.class.getClassLoader().getResource("devices/devices.xml")),
          Path.of(PathManager.getSystemPath(), "devices").toFile(),
          "devices_temp.xml",
          "devices.xml");
  }

  @NotNull
  public static DeviceDefinitionDownloadService getInstance() {
    return ApplicationManager.getApplication().getService(DeviceDefinitionDownloadService.class);
  }

  public void downloadDefinitionsAsync() {
    // TODO Handle success and failure
    refresh(() -> {
            },
            () -> {
            });
  }

  @Override
  public void loadFromFile(@NotNull URL url) {
    try (var in = url.openStream()) {
      // TODO Do something with the devices
      var devices = DeviceParser.parse(in);
    }
    catch (IOException | SAXException | ParserConfigurationException exception) {
      Logger.getInstance(DeviceDefinitionDownloadService.class).warn(exception);
    }
  }
}
