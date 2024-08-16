package com.example.dagger2app.kotlinfiles


import dagger.Binds
import dagger.Component
import dagger.Module

interface LibraryProvider{
    fun provideLogger(): Logger

}

@Component(
        modules = [LibraryModule::class]
)
interface LibraryComponent: LibraryProvider {
    companion object{
        fun init(): LibraryComponent = DaggerLibraryComponent.builder().build()
    }
}

@Module
interface LibraryModule{

    @Binds
    fun logger(loggerImpl: LoggerImpl): Logger
}
