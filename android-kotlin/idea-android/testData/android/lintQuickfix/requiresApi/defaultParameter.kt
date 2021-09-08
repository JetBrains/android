// INTENTION_TEXT: Add @RequiresApi(LOLLIPOP) Annotation
// INSPECTION_CLASS: com.android.tools.idea.lint.inspections.AndroidLintNewApiInspection
// DEPENDENCY: RequiresApi.java -> android/support/annotation/RequiresApi.java

import android.graphics.drawable.VectorDrawable


fun withDefaultParameter(vector: VectorDrawable = <caret>VectorDrawable()) {

}