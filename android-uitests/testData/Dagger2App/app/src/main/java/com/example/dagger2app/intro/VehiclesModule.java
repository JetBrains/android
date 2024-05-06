package com.example.dagger2app.intro;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class VehiclesModule {

    @Provides
    public Engine provideEngine(){
        return new Engine();
    }

    @Provides
    @Singleton
    public Brand provideBrand(){
        return new Brand("Mercedes");
    }
}
