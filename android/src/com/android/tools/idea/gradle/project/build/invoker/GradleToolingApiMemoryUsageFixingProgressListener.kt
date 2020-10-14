/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker

import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor
import org.gradle.tooling.events.task.internal.DefaultTaskStartEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor
import java.lang.reflect.Field

/**
 * A [ProgressListener] that deduplicates instances of [InternalOperationDescriptor] deserialized by the Gradle toolign API connection.
 *
 * The existing versions of the Gradle tooling API/Gradle create new copies of [DefaultTaskDescriptor] representing task dependencies for
 * each [TaskProgressEvent] received. This results in huge memory usage in the IDE process when building large projects since both
 * the build analyzer and the build output tool window subscribe to [OperationType.TASK] event category.
 *
 * The listener disconnects itself in the case of any error/exception and, also, catches and swallows such exceptions.
 */
class GradleToolingApiMemoryUsageFixingProgressListener : ProgressListener {
    var disabled = false
    var dependenciesField: Field? = null
    val descriptors: HashMap<Any, InternalOperationDescriptor> = HashMap()

    private fun process(internalDescriptor: InternalOperationDescriptor): InternalOperationDescriptor {
        val existing = descriptors.putIfAbsent(internalDescriptor.id, internalDescriptor)
        if (existing != null) {
            return existing
        }

        val depField: Field = dependenciesField ?: run<Field> {
            // Access org.gradle.internal.build.event.types.DefaultTaskDescriptor.dependencies field which might hold copies of
            // org.gradle.internal.build.event.types.DefaultTestDescriptor which have already been made available while processing previous
            // events.
            val field: Field? = internalDescriptor.javaClass.getDeclaredField("dependencies")
            if (field == null) {
                disabled = true
                error("dependencies field not found")
            }
            field.isAccessible = true
            dependenciesField = field
            field
        }

        val dependencies = depField.get(internalDescriptor) as LinkedHashSet<*>
        if (dependencies.isNotEmpty()) {
            // Replace [DefaultTestDescriptor] references with the canonical ones.
            val newDep = dependencies.map { process((it as InternalOperationDescriptor)) }
            depField.set(internalDescriptor, LinkedHashSet(newDep))
        }
        return internalDescriptor
    }


    override fun statusChanged(event: ProgressEvent) {
        if (disabled) return
        try {
            val internalDescriptor = when (event) {
                is DefaultTaskStartEvent -> (event.descriptor as? DefaultTaskOperationDescriptor)?.internalOperationDescriptor
                is DefaultTaskFinishEvent -> (event.descriptor as? DefaultTaskOperationDescriptor)?.internalOperationDescriptor
                else -> null
            }
            if (internalDescriptor != null) {
                process(internalDescriptor)
            }
        } catch (e: Exception) {
            disabled = true
        }
    }
}
