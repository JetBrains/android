package com.example.dagger2app.intro


import javax.inject.Inject

class Car @Inject
constructor(var engine: Engine?, var brand: Brand?)
