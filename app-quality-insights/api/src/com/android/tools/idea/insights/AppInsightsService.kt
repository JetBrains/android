package com.google.services.firebase.insights

import com.android.tools.idea.insights.Frame
import com.google.services.firebase.insights.AppInsightsModel.AppConfiguration
import com.google.services.firebase.insights.AppInsightsModel.Unauthenticated
import com.google.services.firebase.insights.analysis.Cause
import com.google.services.firebase.insights.datamodel.CrashlyticsIssue
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.coroutines.flow.Flow

/**
 * Models App Insight states.
 *
 * App Insights functionality currently has a hard dependency on the user being logged into Android
 * Studio with their Google account. Without it, none of the functionality is meaningful since we
 * are unable to make any API calls. To express this dependency the model provides [Unauthenticated]
 * and [AppConfiguration] states.
 */
sealed class AppInsightsModel {
  /** The user is not signed in, App Insights will not work until they do. */
  object Unauthenticated : AppInsightsModel()

  /**
   * When the user is signed in, we provide a list of all Android application modules with their
   * [configured firebase credentials][FirebaseApp] (if available).
   */
  data class AppConfiguration(val availableApps: List<FirebaseApp>) : AppInsightsModel()
}

/**
 * Models a Crashlytics finding in the code.
 *
 * Do not keep references to instances of this class any longer that you would to for plain
 * [PsiElement]s.
 */
data class CrashlyticsInsight(
  /** [line] where the Insight was found. */
  val line: Int,

  /** [CrashlyticsIssue] found in this element. */
  val issue: CrashlyticsIssue,

  /** The stack frame referencing this [element]. */
  val stackFrame: Frame,

  /** What triggered this error. */
  val cause: Cause,

  /**
   * Lambda used to make this insight the active/selected one.
   *
   * The [CrashlyticsState] uses the concept of a [Selection] to represent the list of issues, and
   * to mark one of them as the currently active/selected one. This action modifies the state this
   * Insight belongs to mark it as selected.
   */
  private val markAsSelectedCallback: (CrashlyticsIssue) -> Unit,
) {
  fun markAsSelected() {
    Logger.getInstance(CrashlyticsInsight::class.java).info("Mark issue as selected $issue")
    markAsSelectedCallback(issue)
  }
}

/** Project-level [Service] that provides App Insights data for Android app modules. */
interface AppInsightsService {
  val configuration: Flow<AppInsightsModel>

  /** Returns an [CrashlyticsModuleController] for a given [FirebaseApp]. */
  fun getController(app: FirebaseApp): CrashlyticsModuleController

  /** Return the list of [CrashlyticsInsight]s associated with the [file] */
  fun analyzeFile(file: PsiFile): List<CrashlyticsInsight>

  companion object {
    fun getInstance(project: Project): AppInsightsService = project.service()
  }
}
