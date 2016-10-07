/*
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * A wizard is a series of steps, where each step collects a bit of information and passes it on
 * for some final, accumulated purpose. As the user proceeds along, they can choose to go forward
 * (once the step allows it), backwards, or, at any time, may cancel the wizard.
 * <p/>
 * In this framework, a {@link com.android.tools.idea.wizard.model.ModelWizard} owns one or more
 * {@link com.android.tools.idea.wizard.model.ModelWizardStep}s, and each step is
 * associated with exactly one {@link com.android.tools.idea.wizard.model.WizardModel}. Multiple
 * steps may (and often should) reference the same model.
 * <p/>
 * The model is responsible for storing all data collected by relevant steps and, upon the wizard's
 * completion, executing that data in some useful way. For example, an installer might collect
 * which components a user wants to install and various target paths. Then, when the user hits
 * 'finish', the model would be responsible for unzipping and copying all data over to some final
 * location.
 * <p/>
 * Most wizards are simple enough that they can be represented by a bunch of steps which all
 * reference the same, single model. However, more complex wizards may benefit by breaking up their
 * responsibilities into separate models. This is particularly useful for reusing steps across
 * wizards. For example, you may have one wizard that asks the user to create a new project and
 * then some initial module to populate it with. You may also want another wizard which all it does
 * is create a new module within the active project. In that case, it makes sense to have a
 * ProjectModel and a ModuleModel, where the first wizard interacts with both and the second wizard
 * only the latter.
 * <p/>
 * Finally, this wizard code makes heavy use of the various
 * {@link com.android.tools.idea.ui.properties.AbstractProperty} classes, in order to bind data
 * values to UI components. It's recommended you take a moment and read the package level
 * documentation for properties before using this wizard framework.
 */
package com.android.tools.idea.wizard.model;