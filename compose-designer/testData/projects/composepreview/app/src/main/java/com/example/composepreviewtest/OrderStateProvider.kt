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
package com.example.composepreviewtest

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.example.composepreviewtest.model.OrderDetails
import com.example.composepreviewtest.model.OrderState

class OrderStateProvider : PreviewParameterProvider<OrderState> {
  override val values: Sequence<OrderState> = sequenceOf(basicOrder, loadingOrder, withOrders)

  companion object {
    val order1 = OrderDetails(id = 1L, orderId = 56789L, customerEmail = "john.doe@example.com")

    val order2 = OrderDetails(id = 2L, orderId = 12345L, customerEmail = "jane.smith@example.com")

    val order3 = OrderDetails(id = 3L, orderId = 1003L, customerEmail = "john.doe@example.com")

    private val basicOrder = OrderState()
    private val loadingOrder = OrderState(isLoading = true)
    private val withOrders = OrderState(orders = listOf(order1, order2, order3))
  }
}
