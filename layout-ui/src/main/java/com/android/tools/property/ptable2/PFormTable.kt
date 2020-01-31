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
package com.android.tools.property.ptable2

import com.android.tools.property.ptable2.impl.PFormTableImpl
import javax.swing.table.TableModel

/**
 * A table where the <tab> order includes all the editors in the table.
 *
 * By default a Swing table will <tab> over the table and use arrow keys for
 * navigation inside the table. Sometimes it is convenient to make the table
 * feel like a form although the underlying implementation is still a table.
 */
open class PFormTable(model: TableModel) : PFormTableImpl(model)
