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
import com.android.tools.profiler.proto.Common;
import com.android.tools.idea.io.grpc.stub.StreamObserver;

import static org.mockito.Mockito.*;

public class DataStorePollerTest {

  protected static final int TEST_SESSION_ID = 4321;
  protected static final int TEST_DEVICE_ID = 1234;
  protected static final int TEST_APP_ID = 5678;

  protected static final Common.Device DEVICE =
    Common.Device.newBuilder().setDeviceId(TEST_DEVICE_ID).setBootId("TEST_BOOT_ID").setSerial("TEST_DEVICE_SERIAL").build();

  protected static final Common.Stream STREAM =
    Common.Stream.newBuilder().setStreamId(DEVICE.getDeviceId()).setType(Common.Stream.Type.DEVICE).setDevice(DEVICE).build();

  protected static final Common.Session SESSION =
    Common.Session.newBuilder().setSessionId(TEST_SESSION_ID).setStreamId(TEST_DEVICE_ID).setPid(TEST_APP_ID).build();

  private PollTicker myPollTicker = new PollTicker();

  protected PollTicker getPollTicker() {
    return myPollTicker;
  }

  protected <E> void validateResponse(StreamObserver<E> observer, E expected) {
    verify(observer, times(1)).onNext(expected);
    verify(observer, times(1)).onCompleted();
    verify(observer, never()).onError(any(Throwable.class));
  }

  protected static class PollTicker {
    private Runnable myLastRunner;

    public void run(Runnable runner) {
      myLastRunner = runner;
      run();
    }

    public void run() {
      if (myLastRunner != null) {
        if (myLastRunner instanceof PollRunner) {
          PollRunner poller = ((PollRunner)myLastRunner);
          poller.poll();
        }
        else {
          myLastRunner.run();
        }
      }
    }
  }
}
