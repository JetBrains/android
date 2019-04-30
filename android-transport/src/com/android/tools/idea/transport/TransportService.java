/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.diagnostics.crash.exception.NoPiiException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import java.io.File;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

/**
 * An application-level service for establishing a connection to a device, which can then be used to retrieve Android system and app data.
 * The service is application-level because devices/processes are accessible through multiple projects, and we want the pipeline to work
 * across project where users can use different client features in multiple studio instances.
 */
public class TransportService implements Disposable {
  public static TransportService getInstance() {
    return ServiceManager.getService(TransportService.class);
  }

  private static Logger getLogger() {
    return Logger.getInstance(TransportService.class);
  }

  public static boolean isServiceInitialized() {
    return ourServiceInitialized;
  }

  private static final String DATASTORE_NAME = "DataStoreService";
  private static boolean ourServiceInitialized = false;

  @NotNull private final MessageBus myMessageBus;
  @NotNull private final DataStoreService myDataStoreService;
  @NotNull private final TransportDeviceManager myDeviceManager;

  private TransportService() {
    String datastoreDirectory = Paths.get(PathManager.getSystemPath(), ".android").toString() + File.separator;
    myDataStoreService =
      new DataStoreService(DATASTORE_NAME, datastoreDirectory, ApplicationManager.getApplication()::executeOnPooledThread,
                           new IntellijLogService());
    myDataStoreService.setNoPiiExceptionHandler((t) -> getLogger().error(new NoPiiException(t)));

    myMessageBus = ApplicationManager.getApplication().getMessageBus();
    myDeviceManager = new TransportDeviceManager(myDataStoreService, myMessageBus);

    Disposer.register(this, myDataStoreService::shutdown);
    Disposer.register(this, myDeviceManager);

    ourServiceInitialized = true;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public MessageBus getMessageBus() {
    return myMessageBus;
  }

  @NotNull
  public String getChannelName() {
    return DATASTORE_NAME;
  }
}
