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
import io.grpc.stub.StreamObserver;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class DataStorePollerTest {


  protected static final Common.Session SESSION = Common.Session.newBuilder()
    .setBootId("TEST_BOOT_ID")
    .setDeviceSerial("TEST_DEVICE_SERIAL")
    .build();

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
