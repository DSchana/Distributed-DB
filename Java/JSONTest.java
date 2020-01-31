import java.util.*;
import java.lang.reflect.*;
import com.google.gson.*;
import com.google.gson.reflect.*;

public class JSONTest{
    enum Types {Boolean, Byte, Character, Short, Double, Float, Integer, Long, String};
    public static Gson gson;
    public static void main(String[] args){
        gson = new GsonBuilder().setPrettyPrinting().create();
        KeyValueStore<String, Integer> kvs = new KeyValueStore<String, Integer>();
        for(int i =0 ; i<10; i+=2){
            kvs.upSert(i+":",i+1);
        }
        System.out.println(kvs);
        Type collectionType = new TypeToken<HashMap<String, Object>>(){}.getType();
        System.out.println("___________________________________");
        HashMap<String, Object> output = gson.fromJson(kvs.toString(), collectionType);
        System.out.println(output);
    }
}