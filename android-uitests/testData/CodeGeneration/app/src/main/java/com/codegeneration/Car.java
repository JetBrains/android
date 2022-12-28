package com.codegeneration;

public class Car {

    private String make;
    private String model;
    private int year;

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public int getYear() {
        return year;
    }

    public void setMake(String make) {
       this.make = (make.equals(""))? "BMW" : make;
    }

    public void setModel(String model) {
        //this.model = (model.equals(""))? "M5" : model;

        if(model.equals("")){
            this.model = "M5";
        }
        else{
            this.model = model;
        }
    }

    public void setYear(int year) {
        this.year = year;
    }

    public static Car getInstance(){
        return new Car();
    }
}