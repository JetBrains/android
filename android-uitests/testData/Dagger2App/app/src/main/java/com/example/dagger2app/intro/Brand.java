package com.example.dagger2app.intro;

public class Brand {

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private String name;

    public  Brand(String name){
        this.name = name;
    }
}
