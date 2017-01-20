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
package com.android.tools.datastore.poller;

import com.android.tools.datastore.DataStorePollerTest;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.datastore.TestGrpcService;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.ProfilerServiceGrpc;
import io.grpc.internal.Stream;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ProfilerServiceTest extends DataStorePollerTest {

  private static final String DEVICE_SERIAL = "SomeSerialId";

  private DataStoreService myDataStore = mock(DataStoreService.class);
  private ProfilerService myProfilerService = new ProfilerService(myDataStore);
  @Rule
  public TestGrpcService<FakeProfilerService> myService = new TestGrpcService<>(myProfilerService, new FakeProfilerService());

  @Before
  public void setUp() throws Exception {
    myProfilerService.connectService(myService.getChannel());
  }

  @After
  public void tearDown() throws Exception {
    myDataStore.shutdown();
  }

  @Test
  public void testGetTimes() throws Exception {
    StreamObserver<Profiler.TimesResponse> observer = mock(StreamObserver.class);
    myProfilerService.getTimes(Profiler.TimesRequest.getDefaultInstance(), observer);
    validateResponse(observer, Profiler.TimesResponse.getDefaultInstance());
  }

  @Test
  public void testGetVersion() throws Exception {
    StreamObserver<Profiler.VersionResponse> observer = mock(StreamObserver.class);
    myProfilerService.getVersion(Profiler.VersionRequest.getDefaultInstance(), observer);
    validateResponse(observer, Profiler.VersionResponse.getDefaultInstance());
  }

  @Test
  public void testGetDevices() throws Exception {
    setProcesses();
    StreamObserver<Profiler.GetDevicesResponse> observer = mock(StreamObserver.class);
    Profiler.GetDevicesResponse expected = Profiler.GetDevicesResponse.newBuilder()
      .addDevice(Profiler.Device.newBuilder()
                   .setSerial(DEVICE_SERIAL)
                   .build())
      .build();
    myProfilerService.getDevices(Profiler.GetDevicesRequest.getDefaultInstance(), observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testGetProcesses() throws Exception {
    setProcesses();
    StreamObserver<Profiler.GetProcessesResponse> observer = mock(StreamObserver.class);
    Profiler.GetProcessesRequest request = Profiler.GetProcessesRequest.newBuilder()
      .setDeviceSerial(DEVICE_SERIAL)
      .build();
    Profiler.GetProcessesResponse expected = Profiler.GetProcessesResponse.newBuilder()
      .addProcess(Profiler.Process.getDefaultInstance())
      .build();
    myProfilerService.getProcesses(request, observer);
    validateResponse(observer, expected);
  }

  @Test
  public void testConnect() throws Exception {
    StreamObserver<Profiler.ConnectResponse> observer = mock(StreamObserver.class);
    myProfilerService.connect(Profiler.ConnectRequest.newBuilder().setPort(1234).build(), observer);
    verify(myDataStore, times(1)).connect(1234);
    validateResponse(observer, Profiler.ConnectResponse.getDefaultInstance());
  }

  @Test
  public void testDisconnect() throws Exception {
    StreamObserver<Profiler.DisconnectResponse> observer = mock(StreamObserver.class);
    myProfilerService.disconnect(Profiler.DisconnectRequest.getDefaultInstance(), observer);
    verify(myDataStore, times(1)).disconnect();
    validateResponse(observer, Profiler.DisconnectResponse.getDefaultInstance());
  }

  private void setProcesses() {
    Profiler.SetProcessesRequest request = Profiler.SetProcessesRequest.newBuilder()
      .addDeviceProcesses(Profiler.DeviceProcesses.newBuilder()
                            .setDevice(Profiler.Device.newBuilder()
                                         .setSerial(DEVICE_SERIAL)
                                         .build())
                            .addProcess(Profiler.Process.getDefaultInstance()))
      .build();
    StreamObserver<Profiler.SetProcessesResponse> observer = mock(StreamObserver.class);
    myProfilerService.setProcesses(request, observer);
    validateResponse(observer, Profiler.SetProcessesResponse.getDefaultInstance());
  }


  private static class FakeProfilerService extends ProfilerServiceGrpc.ProfilerServiceImplBase {
    @Override
    public void getTimes(Profiler.TimesRequest request, StreamObserver<Profiler.TimesResponse> responseObserver) {
      responseObserver.onNext(Profiler.TimesResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getVersion(Profiler.VersionRequest request, StreamObserver<Profiler.VersionResponse> responseObserver) {
      responseObserver.onNext(Profiler.VersionResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }
  }
}
