/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.intention

import com.android.SdkConstants
import com.android.tools.idea.kotlin.isSubclassOf
import com.intellij.psi.PsiClass
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.manifest.Application
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.manifest.Service
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.android.isSubclassOf
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtClass


class AddServiceToManifest : AbstractRegisterComponentAction<Service>(
    text = "Add service to manifest",
    componentClassName = SdkConstants.CLASS_SERVICE,
) {
    override fun Application.getCurrentComponents(): List<Service> = services
    override fun Application.addComponent(): Service = addService()
    override fun Service.getComponentClass(): AndroidAttributeValue<PsiClass> = serviceClass
}
