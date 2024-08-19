package com.example.dagger2app.intro

import javax.inject.Singleton

import dagger.Module
import dagger.Provides

@Module
class VehiclesModule {

    @Provides
    fun provideEngine(): Engine {
        return Engine()
    }

    @Provides
    @Singleton
    fun provideBrand(): Brand {
        return Brand("Mercedes")
    }
}
