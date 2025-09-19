/*
 * Copyright (C) 2025 The Android Open Source Project
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
package google.simpleapplication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Conversation(uiState: ConversationUiState, onMessageClick: (Message) -> Unit = {}) {
  Scaffold(topBar = { Text("${uiState.channelName} (${uiState.channelMembers})") }) { paddingValues
    ->
    Column(Modifier.fillMaxSize().padding(paddingValues)) {
      Messages(
        messages = uiState.messages,
        onMessageClick = onMessageClick,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
fun Messages(
  messages: List<Message>,
  modifier: Modifier = Modifier,
  onMessageClick: (Message) -> Unit = {},
) {
  Box(modifier = modifier) {
    LazyColumn(reverseLayout = true, modifier = Modifier.fillMaxSize()) {
      for (index in messages.indices) {
        val message = messages[index]
        item {
          Row(Modifier.clickable(true, onClick = { onMessageClick(message) })) {
            Text("${message.author}: ${message.content} (${message.timestamp})")
          }
        }
      }
    }
  }
}
