/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.io.grpc.*;

import java.io.FileNotFoundException;

public class TestServerInterceptor implements ServerInterceptor {

  TestGrpcFile myFile;

  TestServerInterceptor(TestGrpcFile validationFile) throws FileNotFoundException {
    myFile = validationFile;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata metadata,
                                                               ServerCallHandler<ReqT, RespT> handler) {
    String methodName = call.getMethodDescriptor().getFullMethodName();
    ServerCall.Listener<ReqT> resp = handler.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
      @Override
      public void sendMessage(RespT msg) {
        myFile.recordCall(methodName, msg.getClass().toString(), msg.toString());
        super.sendMessage(msg);
      }
    }, metadata);
    return resp;
  }
}
