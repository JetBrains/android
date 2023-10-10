// INTENTION_CLASS: org.jetbrains.kotlin.android.intention.ImplementParcelableAction
// SKIP_K2
// WITH_STDLIB

class <caret>WithTransient() {
    @Transient var transientText: String = ""
    var text: String = ""
}