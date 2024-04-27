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
package com.google.gct.studio.samples;

import com.appspot.gsamplesindex.samplesindex.SamplesIndex;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class SamplesService {
  public static final String DEFAULT_ROOT_URL = "https://gsamplesindex.appspot.com/_ah/api/";
  public static final String DEFAULT_SERVICE_PATH = "samplesindex/v1/";

  @NotNull
  SamplesIndex getIndex() {
    String url = Optional.ofNullable(System.getProperty("samples.service.use.local.port.for.test"))
      .map(localTestingPort -> "http://127.0.0.1:" + localTestingPort)
      .orElse(DEFAULT_ROOT_URL);
    return getIndex(url, DEFAULT_SERVICE_PATH);
  }

  @NotNull
  SamplesIndex getIndex(String rootUrl, String servicePath) {
    SamplesIndex.Builder myBuilder = new SamplesIndex.Builder(new NetHttpTransport(), new GsonFactory(), rootUrl, servicePath, null);
    return myBuilder.build();
  }

  public static @NotNull SamplesService getInstance() {
    return ApplicationManager.getApplication().getService(SamplesService.class);
  }
}
