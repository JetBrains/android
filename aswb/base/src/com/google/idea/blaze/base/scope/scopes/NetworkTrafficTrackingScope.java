/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.scope.scopes;

import com.google.common.util.concurrent.AtomicLongMap;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink.Propagation;
import com.google.idea.blaze.common.Output;
import java.util.Map;

/** Scope used to measure the number of bytes downloaded over the network. */
public class NetworkTrafficTrackingScope implements BlazeScope {

  /**
   * Output class used to indicate that some network traffic was used.
   *
   * <p>This information is summarised in {@link #getNetworkUsage()}.
   */
  public static class NetworkTrafficUsedOutput implements Output {
    private final long bytesConsumed;
    private final String reason;

    public NetworkTrafficUsedOutput(long bytesConsumed, String reason) {
      this.bytesConsumed = bytesConsumed;
      this.reason = reason;
    }
  }

  private final AtomicLongMap<String> networkTrafficUsed = AtomicLongMap.create();

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(
        NetworkTrafficUsedOutput.class,
        o -> {
          networkTrafficUsed.addAndGet(o.reason, o.bytesConsumed);
          return Propagation.Stop;
        });
  }

  /**
   * Returns the total number of network bytes notified via {@link NetworkTrafficUsedOutput}.
   *
   * <p>Map keys are the {@code reason}s given and the values are the sum of all network traffic
   * with that reason.
   */
  public Map<String, Long> getNetworkUsage() {
    return networkTrafficUsed.asMap();
  }
}
