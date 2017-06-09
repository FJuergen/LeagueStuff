package main.java.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class JsonPrinter {

    public static void printJSonObject(JsonObject object){
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(object));
    }
}
