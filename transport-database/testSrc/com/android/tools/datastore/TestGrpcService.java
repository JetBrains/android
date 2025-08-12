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

import com.android.test.testutils.TestUtils;
import com.android.tools.idea.io.grpc.BindableService;
import com.android.tools.idea.io.grpc.Channel;
import com.android.tools.idea.io.grpc.ClientInterceptors;
import com.android.tools.idea.io.grpc.Server;
import com.android.tools.idea.io.grpc.ServerInterceptors;
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder;
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;

/**
 * JUnit rule for tests that have a datastore and need to fake a connection to it.
 * <p>
 * This rule creates a test instance of {@link StudioProfilers} connected over a light,
 * in-process GRPC server that reads from a mock service which provides fake data. Besides creating
 * the {@link StudioProfilers} instance, it also starts up / shuts down the test server automatically.
 */
public final class TestGrpcService extends ExternalResource {
  private final List<BindableService> myServices;
  private String myGrpcName;
  private Server myServer;
  private ServicePassThrough myDataStoreService;
  private Path myTestFile;
  private DataStoreDatabase myDatabase;
  private TestGrpcFile myRpcFile;
  private TestName myMethodName;
  private String myTestClassName;

  /**
   * @param testClass        The owning test class, used for generating a unique name for a call
   *                         history file
   * @param methodName       The name of the test, used for generating a unique name for a call
   *                         history file
   * @param dataStoreService The main datastore service under test
   * @param services         All services that the datastore under test depends on. These services
   *                         are usually fakes.
   */
  public TestGrpcService(Class testClass,
                         TestName methodName,
                         ServicePassThrough dataStoreService,
                         BindableService... services) {
    myServices = Arrays.asList(services);
    myMethodName = methodName;
    myTestClassName = testClass.getSimpleName();
    myDataStoreService = dataStoreService;
  }

  @Override
  protected void before() throws Throwable {
    myRpcFile = new TestGrpcFile(File.separator + myTestClassName + File.separator + myMethodName.getMethodName());
    myGrpcName = UUID.randomUUID().toString();
    InProcessServerBuilder builder = InProcessServerBuilder.forName(myGrpcName).directExecutor();
    TestServerInterceptor interceptor = new TestServerInterceptor(myRpcFile);
    for (BindableService service : myServices) {
      builder.addService(ServerInterceptors.intercept(service, interceptor));
    }
    myServer = builder.build();
    myServer.start();
    // TODO: Update to work on windows. PathUtil.getTempPath() fails with bazel
    myTestFile = TestUtils.createTempDirDeletedOnExit().resolve("datastoredb");
    myDatabase = new DataStoreDatabase(myTestFile.toString(), DataStoreDatabase.Characteristic.DURABLE, new FakeLogService());
    myDataStoreService.getBackingNamespaces()
                      .forEach(namespace -> myDataStoreService.setBackingStore(namespace, myDatabase.getConnection()));
  }

  @Override
  protected void after() {
    myServer.shutdownNow();
    myDatabase.disconnect();
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

  public Channel getChannel() {
    return ClientInterceptors.intercept(
      InProcessChannelBuilder.forName(myGrpcName).usePlaintext().build(), new TestClientInterceptor(myRpcFile));
  }
}
