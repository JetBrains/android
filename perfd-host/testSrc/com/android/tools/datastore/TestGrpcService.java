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
package com.android.tools.datastore;

import com.android.tools.datastore.poller.PollRunner;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.Function;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JUnit rule for creating a test instance of {@link StudioProfilers} connected over a light,
 * in-process GRPC server that reads from a mock service which provides fake data. Besides creating
 * the {@link StudioProfilers} instance, it also starts up / shuts down the test server automatically.
 *
 * Within a test, use {@link #get()} to fetch a valid {@link StudioProfilers} instance.
 */
public final class TestGrpcService<S extends BindableService> extends ExternalResource {
  public interface BeforeTick { void apply(); }
  private String myGrpcName;
  private final S myService;
  private final BindableService mySecondaryService;
  private Server myServer;
  private ServicePassThrough myDataStoreService;
  private File myTestFile;
  private DataStoreDatabase myDatabase;

  public TestGrpcService(ServicePassThrough dataStoreService, S service) {
    this(dataStoreService, service, null);
  }

  public TestGrpcService(ServicePassThrough dataStoreService, S service, BindableService secondaryService) {
    myService = service;
    mySecondaryService = secondaryService;
    myDataStoreService = dataStoreService;
  }

  @Override
  protected void before() throws Throwable {
    myGrpcName = UUID.randomUUID().toString();
    InProcessServerBuilder builder = InProcessServerBuilder.forName(myGrpcName);
    builder.addService(myService);
    if (mySecondaryService != null) {
      builder.addService(mySecondaryService);
    }
    myServer = builder.build();
    myServer.start();
    // TODO: Update to work on windows. PathUtil.getTempPath() fails with bazel
    myTestFile = new File("/tmp/datastoredb");
    myDatabase = new DataStoreDatabase(myTestFile.getAbsolutePath());
    myDatabase.registerTable(myDataStoreService.getDatastoreTable());
  }

  @Override
  protected void after() {
    myServer.shutdownNow();
    myDatabase.disconnect();
    myTestFile.delete();
  }

  public void shutdownServer() {
    myServer.shutdownNow();
  }

  public ManagedChannel getChannel() {
    return InProcessChannelBuilder.forName(myGrpcName)
      .usePlaintext(true)
      .build();
  }
}
