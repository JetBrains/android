val activity = mapOf<String,String>()
activity["foo"] = "bar"
val deps by extra(mapOf<String,Any>())
val newDeps by extra(mapOf("newActivity" to activity))
deps["activity"] = activity
