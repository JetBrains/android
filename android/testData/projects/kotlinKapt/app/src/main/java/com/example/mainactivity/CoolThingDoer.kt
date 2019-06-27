package com.example.mainactivity

import dagger.Component

@Component
interface CoolThingDoer {
    fun doCoolThing(app: MainActivity)
}