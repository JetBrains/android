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

import com.google.common.collect.Lists;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * JUnit rule for creating a test instance of {@link StudioProfilers} connected over a light,
 * in-process GRPC server that reads from a mock service which provides fake data. Besides creating
 * the {@link StudioProfilers} instance, it also starts up / shuts down the test server automatically.
 *
 * Within a test, use {@link #get()} to fetch a valid {@link StudioProfilers} instance.
 */
public final class TestGrpcService extends ExternalResource {
  private final List<BindableService> myServices;
  private String myGrpcName;
  private Server myServer;
  private ServicePassThrough myDataStoreService;
  private File myTestFile;
  private DataStoreDatabase myDatabase;
  private TestGrpcFile myRpcFile;
  private TestName myMethodName;
  private String myTestClassName;

  public TestGrpcService(Class testClazz,
                         TestName methodName,
                         ServicePassThrough dataStoreService,
                         BindableService primaryService,
                         BindableService... otherServices) {
    myServices = new ArrayList<>();
    myServices.add(primaryService);
    myServices.addAll(Arrays.asList(otherServices));
    myMethodName = methodName;
    myTestClassName = testClazz.getSimpleName();
    myDataStoreService = dataStoreService;
  }

  @Override
  protected void before() throws Throwable {
    myRpcFile = new TestGrpcFile(File.separator + myTestClassName + File.separator + myMethodName.getMethodName());
    myGrpcName = UUID.randomUUID().toString();
    InProcessServerBuilder builder = InProcessServerBuilder.forName(myGrpcName);
    TestServerInterceptor interceptor = new TestServerInterceptor(myRpcFile);
    for (BindableService service : myServices) {
      builder.addService(ServerInterceptors.intercept(service, interceptor));
    }
    myServer = builder.build();
    myServer.start();
    // TODO: Update to work on windows. PathUtil.getTempPath() fails with bazel
    myTestFile = new File("/tmp/datastoredb");
    myDatabase = new DataStoreDatabase(myTestFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myDataStoreService.getBackingNamespaces()
      .forEach(namespace -> myDataStoreService.setBackingStore(namespace, myDatabase.getConnection()));
  }

  @Override
  protected void after() {
    myServer.shutdownNow();
    myDatabase.disconnect();
    myTestFile.delete();
    try {
      // Validate the gRPC call execution order in its entirety makes it easier to view diffs.
      myRpcFile.closeAndValidate();
    }
    catch (IOException ex) {
      // Failed to close file handle.
      Assert.fail("Failed to validate test: " + ex);
    }
  }

  public void shutdownServer() {
    myServer.shutdownNow();
  }

  public Channel getChannel() throws FileNotFoundException {
    return ClientInterceptors.intercept(InProcessChannelBuilder.forName(myGrpcName)
                                          .usePlaintext(true)
                                          .build(), new TestClientInterceptor(myRpcFile));
  }
}
