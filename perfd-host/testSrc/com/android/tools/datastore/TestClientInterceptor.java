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

import io.grpc.*;

import java.io.FileNotFoundException;

public class TestClientInterceptor implements ClientInterceptor {

  private TestGrpcFile myFile;

  TestClientInterceptor(TestGrpcFile validationFile) throws FileNotFoundException {
    myFile = validationFile;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> descriptor,
                                                             CallOptions options,
                                                             Channel channel) {
    String methodName = descriptor.getFullMethodName();
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(descriptor, options)) {
      @Override
      public void sendMessage(ReqT msg) {
        myFile.recordCall(methodName, msg.getClass().toString(), msg.toString());
        super.sendMessage(msg);
      }
    };
  }
}
