/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.client

import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.Any
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionMessage
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionOutput
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionRequest
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionResponse
import com.google.cloud.cloudaicompanion.v1main.TaskCompletionServiceGrpc
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.DisposableRule
import com.studiogrpc.testutils.ForwardingInterceptor
import com.studiogrpc.testutils.GrpcConnectionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class TitanAiInsightClientTest {
  private val service = FakeTitanGrpcService()
  @get:Rule val grpcRule = GrpcConnectionRule(listOf(service))
  @get:Rule val disposableRule = DisposableRule()

  @Test
  fun `test titan client`() = runBlocking {
    val client = TitanAiInsightClient(grpcRule.channel, ForwardingInterceptor)

    val projectId = "project-id"
    val insight = client.fetchCrashInsight(projectId) { Any.newBuilder().build() }
    assertThat(insight.rawInsight).isEqualTo("Task Complete Response")
  }
}

private class FakeTitanGrpcService : TaskCompletionServiceGrpc.TaskCompletionServiceImplBase() {

  override fun completeTask(
    request: TaskCompletionRequest,
    responseObserver: StreamObserver<TaskCompletionResponse>,
  ) {

    assertThat(request.input).isEqualTo(DEFAULT_TASK_COMPLETION_INPUT)
    assertThat(request.instance).isEqualTo("projects/project-id/locations/global/instances/default")
    assertThat(request.experienceContext).isEqualTo(CRASHLYTICS_EXPERIENCE_CONTEXT)

    val response =
      TaskCompletionResponse.newBuilder()
        .apply {
          output =
            TaskCompletionOutput.newBuilder()
              .apply {
                val messageBuilder =
                  TaskCompletionMessage.newBuilder().apply { content = "Task Complete Response" }
                addMessages(messageBuilder)
              }
              .build()
        }
        .build()
    responseObserver.onNext(response)
    responseObserver.onCompleted()
  }
}
