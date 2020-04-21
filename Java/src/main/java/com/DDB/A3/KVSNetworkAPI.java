// Handles calls regarding manipulation of the Key-Value both locally and remotely and backup node interaction
package main.java.com.DDB.A3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/* local 
    - Allow calls to local call and allow us to make calls to other database
*/

/* remote
    - After getting the local command read any command made from 
    - one socket to open connection with random node and handle all the command - optional
*/

/*backup interaction
    - update backup on every call to KVS
    - receive update to backup KVS consistently
    - one node for all backup interaction
    - keep track of next in line
*/
enum KVSCommands {
  QUIT,
  INSERT,
  UPDATE,
  UPSERT,
  GET,
  DELETE,
  FIND,
  CLEAR,
  COUNT,
  GET_ALL
}

public class KVSNetworkAPI {
   
    /* Object used JSON conversion */
    public final Gson gson;
    /* Key Value store */
    private final KeyValueStore<String, Object> kvs;  
    /* controls Access to key-value store */ 
    private final AtomicBoolean metaLock; 
    /* Target port number of the backup socket - used to automatically move to next in line */
    private static int nextInLinePortNumber;
    private static String nextInLineIP= "NONE"; 
    private Socket lastSocket, backupSocket;
    private PrintStream backupWriter;
    private BufferedReader backupReader;
    private static Peer peer;
    public KVSNetworkAPI(){
        metaLock = new AtomicBoolean(false);
        gson =  new GsonBuilder().serializeNulls().create();
        // nextInLinePortNumber = socketNumber;
        backupWriter = null;
        backupSocket = new Socket();
        lastSocket = null;
        kvs = new KeyValueStore<>();
    }
    
    /* A one time initialization done at the start of the program*/
    public static void staticInitialize(Peer p, int socketNumber){
        peer = p;
        nextInLinePortNumber =socketNumber;
    }

    public void run() {

        // IpFinder.findIP();
        int commandValue = 8;  // hold clients command
        Scanner sc = new Scanner(System.in);
        KVSCommands command;
        while(commandValue != 0){
            /* if socket was closed reset it*/
            if(backupSocket.isClosed() || !backupSocket.isConnected()){
                if(backupWriter != null)
                    backupWriter.close();
                backupWriter = null;
                if (!(nextInLineIP.equals("NONE")) ){
                    this.setBackup(nextInLineIP);
                }
            }
            if(backupWriter == null && backupSocket.isConnected()){
                this.initializeBackup();
                var backupListener = new BackupListener(backupReader);
                new Thread(backupListener).start();
                /* ** LISTEN TO NODE THAT IS YOUR BACK UP IN SEPARATE THREAD FOR UPDATE TO NEXT IN LINE  ** */
            }

                commandValue = -1;
                while(commandValue == -1){
                    KVSNetworkAPI.userPrompt();
                    try {
                        commandValue = Integer.parseInt(sc.nextLine().trim()); //if user entered number then get
                    }catch(NumberFormatException e){
                        commandValue = -1;
                        System.out.println("Please enter one of the following numbers");
                    }
                    if(commandValue <0 || commandValue>9){
                        commandValue = -1;
                        System.out.println("Please enter a valid number in the given range");
                    }
                }

           
            command = KVSCommands.values()[commandValue];

            /* Quitting will end the program */ 
            if(command == KVSCommands.QUIT ){
                break; 
            }
            /* Handle the command with two inputs */
            if(command ==  KVSCommands.INSERT || command ==  KVSCommands.UPDATE || command ==  KVSCommands.UPSERT ){
                String[] keyValue = new String[1];
                while(keyValue.length<2){
                    System.out.println("Enter the key value pairs separated with a comma (e.g. key, value)");
                    keyValue = sc.nextLine().split(",");
                }
                
                if(command  == KVSCommands.INSERT){
                    System.out.println(this.insert(keyValue[0], keyValue[1]));
                }
                if(command  == KVSCommands.UPDATE){
                    System.out.println(this.update(keyValue[0], keyValue[1]));
                }
                if(command  == KVSCommands.UPSERT){
                    System.out.println(this.upSert(keyValue[0], keyValue[1]));
                }
            }
            /* Handle the commands with one input */
            else if (command ==  KVSCommands.GET || command ==  KVSCommands.DELETE || command ==  KVSCommands.FIND ) { // get the argument for the command
                System.out.println("Enter the key you want to use for the command");
                var key = sc.nextLine().trim();
                if(command  == KVSCommands.GET){
                    System.out.println(this.get(key));
                }
                if(command  == KVSCommands.DELETE){
                    System.out.println(this.delete(key));
                }
                if(command  == KVSCommands.FIND){
                    System.out.println(this.find(key));
                }
            }
            else if (command != KVSCommands.QUIT ) {
                if(command  == KVSCommands.CLEAR){
                    System.out.println(this.clear());
                }
                if(command  == KVSCommands.COUNT){
                    System.out.println(this.count());
                }
                if(command  == KVSCommands.GET_ALL){
                    System.out.println(this.getAll());
                }
            }
        }
        

    }

    /* prompt for the client */
    private static void userPrompt() {
        System.out.println("Select a command to enter or 0 to quit");
        System.out.println("1.Insert - Create a new key-value pairs");
        System.out.println("2.Update - update key value pairs");
        System.out.println("3.UpSert - update the key value pair or insert if it doesn't exist");
        System.out.println("4.Get - retrieve the values of the corresponding keys");
        System.out.println("5.Delete - remove the values of the corresponding keys");
        System.out.println("6.Find - Check if keys exist in store");
        System.out.println("7.Clear - Empty out the store");
        System.out.println("8.Count - get the size of the key-value store");
        System.out.println("9.Get all - get back all the elements in the Key-value store");
    }

    // get object from JSON file
    public HashMap<String, Object> deserializeJson(String jsonData) {
        Type collectionType = new TypeToken<HashMap<String, Object>>() {}.getType();
        return gson.fromJson(jsonData, collectionType);
    }

    public String insert(String key, Object value) {
        while (metaLock.get()) ; // stay until lock is free
        metaLock.lazySet(true); // set lock
        String output;
        if (kvs.find(key)) // If key already exists then send an error
            output = "ERROR: " + key + " already exist as a key in the database cannot update";
        else {
            kvs.insert(key, value);
            output = "Successfully created Key-Value pair for " + key;
            if(backupWriter != null){
                KVSCommandObject command = new KVSCommandObject("insert", key, gson.toJson(value));
                String message = gson.toJson(command);
                backupWriter.println(message);
            }
        }
        metaLock.lazySet(false); // free up lock
        return output;
    }

    public String get(String key) { // Get Serialized version of value
        if (kvs.find(key)) // If key exists send appropriate value
            return kvs.get(key).toString();
        return "ERROR: The key " + key + ", does not exist in the database, cannot get";
    }

    public String delete(String key) {
        if (!kvs.find(key))
            return "ERROR: The key " + key + ", does not exist in the database, cannot delete";
        while (metaLock.get()) ; // stay until lock is free
        metaLock.lazySet(true); // set lock
        String deletedValue = kvs.delete(key).toString();
        metaLock.lazySet(false); // free up lock
        if(backupWriter != null){
            KVSCommandObject command = new KVSCommandObject("delete", key);
            String message = gson.toJson(command);
            backupWriter.println(message);
        }
        return "The value of the deleted key " + key + " was " + deletedValue;
    }

    public boolean find(String key) {
        return kvs.find(key);
    }

    public String update(String key, Object value) {
        if (kvs.update(key, value)){
            if(backupWriter != null){
                KVSCommandObject command = new KVSCommandObject("update", key, gson.toJson(value));
                String message = gson.toJson(command);
                backupWriter.println(message);
            }
            return "Successfully updated key " + key + "!";
        }
        else
            return "Error: Key not found, server could not update :(";
    }

    public String upSert(String key, Object value) {
        while (metaLock.get()) ; // stay until lock is free
        metaLock.lazySet(true); // set lock just in case we have to add key
        kvs.upSert(key, value);
        metaLock.lazySet(false); // free up lock
        if(backupWriter != null){
            KVSCommandObject command = new KVSCommandObject("upsert", key, gson.toJson(value));
            String message = gson.toJson(command);
            backupWriter.println(message);
        }
        return "Successfully upSerted " + key;
    }

    /* 
    *   Delete all the elements in the KVS
    */
    public String clear() {
        if (count() == 0)
            return "Key-value store is already empty!";
        while (metaLock.get()) ; // stay until lock is free
        metaLock.lazySet(true); // set lock to take control of Key-Value store
        kvs.clear();
        metaLock.lazySet(false); // free up lock
        if(backupWriter != null){
            KVSCommandObject command = new KVSCommandObject("clear");
            String message = gson.toJson(command);
            backupWriter.println(message);
        }
        return "You cleared the Key-Value Store O.O";
    }

    /* 
    *   Get back the number of element in the KVS
    */
    public int count() {
        return kvs.count();
    }

    /* 
    *   Return all the key-value pair in the KVS
    */
    public String getAll(){
        return kvs.toString(); 
    }

    /* Add all the items to the map and update the backup */
    public void addAll(HashMap<String, Object> values){
        // System.out.println("VALUE" + values.toString());
        values.forEach((key,value) -> insert(key,value));

    }

    /* Function to add in new backup node - called by Peer if leader gave us a backup automatically called */
    public void setBackup(String ip){
        /* Let main program know backup changed so node whose back up you are can be informed*/
        KVSCommandObject command = new KVSCommandObject("backup changed", ip,
                                                    String.valueOf(nextInLinePortNumber));
        peer.changedBackup( gson.toJson(command));     
        backupSocket = new Socket();
        try {
            var address =  new InetSocketAddress(InetAddress.getByName(ip), nextInLinePortNumber);
            // System.out.println("Backup try address " + address);
            /* 30 seconds wait to accept */
            backupSocket.connect(address,30000);
            if(backupSocket.getRemoteSocketAddress().equals(backupSocket.getLocalSocketAddress())){
                System.err.println("WARNING: Attempted to connect to self - check for multiple running instances");
            }
        
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
  

    public boolean initializeBackup(){ 
        PrintStream newWriter = null;
        BufferedReader newReader = null;
        try {
            
            newWriter = new PrintStream(backupSocket.getOutputStream());
            newReader = new BufferedReader(new InputStreamReader(backupSocket.getInputStream()));
            newWriter.println(peer.getName());
            do{
                /* Tell backup node how much items you have*/
                newWriter.println(this.count());
                /* Give your backup a copy of your database*/
                newWriter.println(this.getAll());
//                while(!newReader.ready()); // wait for stream to become ready
            } while(!newReader.readLine().trim().equals("SUCCESS"));
            if(peer.isLeader()){
                newWriter.println("LEADER");
                if(backupWriter != null){
                    /*  if old writer is active (will be for leaders) let the back up know you are leaving */
                    backupWriter.println("RELIEVE"); 
                    backupWriter.close();
                }
                /* If we have last socket send that to the new node to add - otherwise the leader is a lone node and will add itself */
                if (lastSocket != null){
                    newWriter.println(lastSocket.getInetAddress().getHostAddress());
                    newWriter.println(lastSocket.getPort());
                }
                else{
                    newWriter.println(backupSocket.getLocalAddress().getHostAddress());
                    newWriter.println(backupSocket.getLocalPort());
                }
            }
            /* Normal case: old back up died so you make a new back up */
            else{
                newWriter.println("NORMAL");
                String ip = newReader.readLine();
                int sn = Integer.parseInt(newReader.readLine());
                this.setNextInLine(ip, sn);
            }
          
            
        } catch (IOException e) {
//            e.printStackTrace();
        }
        finally {
            if (newReader != null )
            backupReader = newReader;
            backupWriter = newWriter;
        }
        return backupWriter != null && backupReader != null; 
    }

    public String getBackupIP(){
        if(backupSocket == null || !backupSocket.isConnected()){
            return "NONE";
        }
        return backupSocket.getInetAddress().getHostAddress().toString();
    }

    public String getBackupPort(){
        return String.valueOf(nextInLinePortNumber);
    }

    public void setNextInLine(String ip, int socketNumber){
        if (!(ip.equals(backupSocket.getLocalAddress().getHostAddress()) && socketNumber == backupSocket.getLocalPort())) {
            // As long as it is not adding itself - it should add the next in line
            System.out.println("Added the IP " + ip + " as next in line ");
            nextInLineIP = ip;
            nextInLinePortNumber = socketNumber;
        }
    }

    public boolean needBackup(){
        return backupWriter == null || backupSocket==null || backupSocket.isClosed();
    }
    private static class KVSCommandObject{
        public String command = null;
        public String key = null;
        public String payload = null;

        KVSCommandObject(String command, String key, String payload){
            this.command = command;
            this.key = key;
            this.payload = payload;
        }

        KVSCommandObject(String command, String key){
            this(command, key, null);
        }

        KVSCommandObject(String command){
            this(command, null, null);
        }
    }

    private static class BackupListener implements Runnable{
        BufferedReader reader;
        Gson gson;
        BackupListener(BufferedReader reader){
            this.reader=reader;
            gson = new Gson();
        }

        @Override
        public void run() {
            /* First line is a throw away saying backup change */
            String prompt, json;
            try {
                while (reader != null && (prompt = reader.readLine()) != null) {
                    if (prompt.trim().equals("BACKUP CHANGE")) {
                        json = reader.readLine();
                        if(json == null)
                            continue;
                        KVSCommandObject command = gson.fromJson(json, KVSCommandObject.class);
                        System.out.println("Back up changer received command " + command.command);
                        nextInLineIP = command.key;
                    }
                }
            }  catch (IOException e) {
//                e.printStackTrace();
            }
        }

    }
}
