/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.tools.idea.adddevicedialog.Table
import com.android.tools.idea.adddevicedialog.TableTextColumn
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.separator

@Composable
internal fun DevicePanel(
  device: VirtualDevice,
  selectedServices: Services?,
  servicesCollection: ImmutableCollection<Services>,
  images: ImmutableList<SystemImage>,
  onDeviceChange: (VirtualDevice) -> Unit,
  onSelectedServicesChange: (Services?) -> Unit,
) {
  Text("Name", Modifier.padding(bottom = Padding.SMALL))

  TextField(
    device.name,
    onValueChange = { onDeviceChange(device.copy(name = it)) },
    Modifier.padding(bottom = Padding.MEDIUM_LARGE),
  )

  Text("Select System Image", Modifier.padding(bottom = Padding.SMALL_MEDIUM))

  Text(
    "Available system images are displayed based on the service and ABI configuration",
    Modifier.padding(bottom = Padding.SMALL_MEDIUM),
  )

  ServicesDropdown(
    selectedServices,
    servicesCollection,
    onSelectedServicesChange,
    Modifier.padding(bottom = Padding.MEDIUM_LARGE),
  )

  SystemImageTable(images)
}

@Composable
private fun ServicesDropdown(
  selectedServices: Services?,
  servicesCollection: ImmutableCollection<Services>,
  onSelectedServicesChange: (Services?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text("Services", Modifier.padding(bottom = Padding.SMALL))

    Row {
      Dropdown(
        Modifier.padding(end = Padding.MEDIUM),
        menuContent = {
          servicesCollection.forEach {
            selectableItem(selectedServices == it, onClick = { onSelectedServicesChange(it) }) {
              Text(it.toString())
            }
          }

          separator()

          selectableItem(selectedServices == null, onClick = { onSelectedServicesChange(null) }) {
            Text("Show All")
          }
        },
      ) {
        Text(selectedServices?.toString() ?: "Show All")
      }

      InfoOutlineIcon(Modifier.align(Alignment.CenterVertically))
    }
  }
}

@Composable
private fun SystemImageTable(images: ImmutableList<SystemImage>) {
  val columns =
    listOf(
      TableTextColumn("System Image", attribute = { _ -> "" }),
      TableTextColumn(
        "Services",
        attribute = { it.services.toString() },
        comparator = Comparator.comparing(SystemImage::services),
      ),
      TableTextColumn("API", attribute = { _ -> "" }),
      TableTextColumn("ABI", attribute = { _ -> "" }),
    )

  // TODO: http://b/339247492 - Stop calling distinct
  Table(columns, images.distinct(), { it })
}
