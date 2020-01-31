import java.util.*;
import java.util.concurrent.Executors; // 
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.*;
import java.io.*;
import java.lang.reflect.*; // Holds the type class
import com.google.gson.*;  // 
import com.google.gson.reflect.*;

public class Server{
    public static final int SOCKET_NUMBER = 2000;
    public static final int NUMBER_CLIENTS = 25;
    public static Set<PrintStream> client= new HashSet<>(); //list of player for broadcast
    public static Gson gson;
    // Key Value store
    public static KeyValueStore<String, Object> kvs;
    // controls Access to key-value store
    public static AtomicBoolean metaLock; 
    public static void main(String[] args){
        kvs = new KeyValueStore<String, Object>();
        metaLock = new AtomicBoolean(false); 
        gson = new GsonBuilder().setPrettyPrinting().create();
        runServer();
    }

    public static void runServer(){
        IpFinder.findIP();
        try (ServerSocket hostSocket= new ServerSocket(SOCKET_NUMBER)) {
            var pool = Executors.newFixedThreadPool(NUMBER_CLIENTS); // cap the amount of maximum client
            //Stay on to lookout for more clients
            while (client.size()<NUMBER_CLIENTS+1) { // this loop should not end 
                pool.execute(new ClientHandler(hostSocket.accept())); 
            }
        }catch (IOException  e) {
            System.out.println("Socket could not be created, check processes permissions");
            System.err.println(e);
        }
        catch (Exception e) {
            System.out.println("Something went wrong :(");
        }
    }
    // get object from JSON file
    public static HashMap<String,Object> deserializeJson(String type, String jsonData){
        /* *** In future I might have to figure out same way to return an object with the appropriate type 
        or atleast hold the type in the key store so that I can initiate it before using ***** */
        /* if(type.equals("integer")) // lowercase to remove case sensitivity
            Type collectionType = new TypeToken<HashMap<Integer>>(){}.getType(); 
        */
        Type collectionType = new TypeToken<HashMap<String, Object>>(){}.getType();
        return gson.fromJson(jsonData, collectionType);
    }

    // ** In future may remove print and send a message to client instead 
    public static String insert(String key, Object value ){ 
        while(metaLock.get()); // stay until lock is free
        metaLock.lazySet(true); // set lock
        String output;
        if(kvs.find(key)) // If key already exists then send an error
            output = "ERROR: "+ key + " already exist as a key in the database cannot update";
        else{
            kvs.insert(key, value);
            output = "Successfully created Key-Value pair for " + key;
        }
        metaLock.lazySet(false); // free up lock
        return output;
    }

    public static String get(String key){ // Get Serialized version of value
        if(kvs.find(key)) // If key exists send appropriate value
            return kvs.get(key).toString();
        return "ERROR: The key " + key + ", does not exist in the database, cannot get";    
    }

    public static String delete(String key){ 
        if(!kvs.find(key))
            return "ERROR: The key " + key + ", does not exist in the database, cannot delete";  
        while(metaLock.get()); // stay until lock is free
        metaLock.lazySet(true); // set lock
        String deletedValue = kvs.delete(key).toString();
        metaLock.lazySet(false); // free up lock
        return deletedValue;
    }

    public static boolean find(String key){
        return kvs.find(key);
    }
    
    public static String update(String key, Object value){
        if(kvs.update(key, value))
            return "Successfully updated key " + key +"!";
        else
            return "Error: Key not found, server could not update :(";
    }

    public static String upSert(String key, Object value){
        while(metaLock.get()); // stay until lock is free
        metaLock.lazySet(true); // set lock just in case we have to add key
        kvs.upSert(key,value);
        metaLock.lazySet(false); // free up lock
        return "Successfully upSerted "+ key;
    }
    public static String clear(){
        while(metaLock.get()); // stay until lock is free
        metaLock.lazySet(true); // set lock to take control of Key-Value store
        kvs.clear();
        metaLock.lazySet(false); // free up lock
        return "You cleared the Key-Value Store O.O";
    }

    public static int count(){
        return kvs.count();
    }
    
    
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintStream p;
        private Scanner sc;
        private String command, payload, className, input;
        // private static final ThreadLocal<String> threadName = Thread.currentThread().getName();
        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                System.out.println("New client joined " +socket);
                client.add(p = new PrintStream(socket.getOutputStream()));
                sc = new Scanner(socket.getInputStream());
                this.protocolPrint();
                while (socket.getInetAddress().isReachable(10)) { // loop while client is active
                    command=sc.nextLine().toLowerCase();
                    className = "CLASS_NAME";
                    payload = ""; // (re)set payload
                    while(sc.hasNextLine()){
                        input = sc.nextLine();
                        if(input.trim().equals("END"))
                            break;
                        payload += input; // get payload                    
                    }
                    // run the clients command
                    this.runCommand();
                    p.println("END"); 
                }           
            }
            catch (IOException e){System.err.println(e);
            }catch (Exception e) { // necessary because we don't want a crash on one client to break the program
                System.out.println("Error:" + socket);
                System.err.println(e);
            } finally {
                if (p!= null) 
                    client.remove(p);
                try { socket.close(); } catch (IOException e) {}
                System.out.println("Closed: " + socket);
            }
        }

        private void protocolPrint(){
            p.println("Protocol:"); 
            p.println("command"); 
            p.println("number of values"); 
            // p.println("class name"); // implement after **
            p.println("payload"); 
            p.println("END"); 
        }

        public void runCommand(){
            p.println("SERVER RETURN"); // let's client know that return is coming
            if (command.equals("insert") || command.equals("update") || command.equals("upsert")){
                HashMap<String, Object> data = deserializeJson(className, payload);
                for (String key : data.keySet()) 
                    if(command.equals("insert"))
                        p.println(insert(key, data.get(key)));
                    else if(command.equals("update"))
                        p.println(update(key, data.get(key)));
                    else if(command.equals("upsert"))
                        p.println(upSert(key, data.get(key)));
            }
            else if( command.equals("get") || command.equals("delete") || command.equals("find")){
                String[] keys;
                keys = gson.fromJson(payload, String[].class); 
                for(String key: keys)
                    if(command.equals("get"))
                        p.println(get(key));
                    else if(command.equals("delete"))
                        p.println(delete(key));
                    //left value as boolean to make it easier for client to use
                    else if(command.equals("find")){
                        p.println(key); 
                        p.println(find(key));      
                    }
            }
            else{ //commands with no parameter
                if(command.equals("clear")){
                    p.println(clear());
                    for (PrintStream  player : client) // let everyone know who cleared they store
                        player.println("Client " + socket + " cleared the key-value store" ); 
                }
                else if(command.equals("count")){
                    p.println(count()); 
                }
            }            
                      
        }
    }
}