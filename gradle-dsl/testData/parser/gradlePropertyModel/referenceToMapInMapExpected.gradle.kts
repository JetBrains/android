val activity = mutableMapOf<String,String>()
activity["foo"] = "bar"
val deps by extra(mutableMapOf<String,Any>())
val newDeps by extra(mapOf("newActivity" to activity))
deps["activity"] = activity
