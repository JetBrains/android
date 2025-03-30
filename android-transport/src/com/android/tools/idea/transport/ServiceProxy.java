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

import com.android.tools.idea.io.grpc.MethodDescriptor;
import com.android.tools.idea.io.grpc.ServerCallHandler;
import com.android.tools.idea.io.grpc.ServerServiceDefinition;
import com.android.tools.idea.io.grpc.ServiceDescriptor;
import com.android.tools.idea.io.grpc.stub.AbstractStub;
import com.android.tools.idea.io.grpc.stub.ClientCalls;
import com.android.tools.idea.io.grpc.stub.ServerCalls;
import com.android.tools.idea.io.grpc.stub.StreamObserver;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public abstract class ServiceProxy {

  private final ServiceDescriptor myServiceDescriptor;

  public ServiceProxy(@NotNull ServiceDescriptor serviceDescriptor) {
    myServiceDescriptor = serviceDescriptor;
  }


  public abstract ServerServiceDefinition getServiceDefinition();

  /**
   * Performs any operations required to free up resources for the proxy service.
   */
  public void disconnect() {
  }

  /**
   * TODO this only handles calls of {@link MethodDescriptor.MethodType.UNARY} type at the moment.
   *
   * @param overrides    a map of overridden descriptor-handlers which do not need forwarding to the stubs.
   * @param blockingStub the stub to redirect any un-mapped unary calls to
   * @return
   */
  protected final ServerServiceDefinition generatePassThroughDefinitions(@NotNull Map<MethodDescriptor, ServerCallHandler> overrides,
                                                                         @NotNull AbstractStub blockingStub) {
    ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(myServiceDescriptor);
    overrides.forEach((method, handler) -> builder.addMethod(method, handler));
    myServiceDescriptor.getMethods().forEach(descriptor -> {
      if (overrides.containsKey(descriptor)) {
        // Method already overridden, skip.
        return;
      }

      switch (descriptor.getType()) {
        case UNARY:
          builder.addMethod(descriptor,
                            ServerCalls.asyncUnaryCall((request, observer) -> {
                              invokeAsyncUnaryCalls(descriptor, blockingStub, request, observer);
                            }));
          break;
        case CLIENT_STREAMING:
        case SERVER_STREAMING:
        case BIDI_STREAMING:
        case UNKNOWN:
          // TODO to be implemented.
          throw new UnsupportedOperationException();
      }
    });

    return builder.build();
  }

  /**
   * A helper method for capturing the wild card for request, so we can cast it to the proper type for the observer.
   */
  private <Req, Resp> void invokeAsyncUnaryCalls(@NotNull MethodDescriptor descriptor,
                                                 @NotNull AbstractStub stubInstance,
                                                 Req request,
                                                 @NotNull StreamObserver<Resp> observer) {
    ClientCalls.asyncUnaryCall(stubInstance.getChannel().newCall(descriptor, stubInstance.getCallOptions()), request, observer);
  }
}
