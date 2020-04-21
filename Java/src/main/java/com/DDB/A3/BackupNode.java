/*  
*	Backup node class handle the server side part of the buddy system - It has access to the KVS network API
*	It handle 2 main types of incoming requests
*   1. Normal backup request - will make a new KVS API to handle as the users back up - will return the next in line
*   2. Handles request to give back old data 
*	
*/


/* Report node will get backup from node which it will send to Peer node which will froward it to the KVS network API - which will send the backup request
*  When a node dies the KVS network API - will get the next in line (the current node's backup ) - which is stored as a static variable 
*/
package main.java.com.DDB.A3;

import com.google.gson.Gson;
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
import java.util.concurrent.Executors;

public class BackupNode implements Runnable{

    /* 
    *   realistically every node will have a max of 3 nodes open at a time
    *   right before it switches backup node 
    *   and for a little while and while it is getting back it's old data
    */
    private final int maxClients; 
    private int socketNumber;
    private HashMap<String,KVSNetworkAPI> backupMap;
    private HashMap<String,KVSNetworkAPI> archivalMap;
    private HashSet<PrintStream> backupWriter;
    public boolean retrieved;
    /* Object used JSON conversion */
    public final Gson gson;
    private Peer peer;

    public BackupNode(int socketNumber, Peer peer) {
        backupMap = new HashMap<>();
        archivalMap = new HashMap<>();
        backupWriter = new HashSet<>();
        maxClients = 3; 
        this.socketNumber = socketNumber;
        gson = new Gson();
        retrieved = false;
        this.peer = peer;
    }

    public void checkReturner(String name, String ip, int port){
        if(archivalMap.containsKey(name) && archivalMap.get(name).count()>0){
            var data = archivalMap.get(name);
            // If have stuff reach out and give back it's stuff
            try (Socket backupSocket = new Socket(InetAddress.getByName(ip), port)) {
                if(backupSocket.getRemoteSocketAddress().equals(backupSocket.getLocalSocketAddress())){
                    System.err.println("WARNING: Attempted to connect to self - check for multiple running instances");
                }
                var newWriter = new PrintStream(backupSocket.getOutputStream());
                var newReader = new BufferedReader(new InputStreamReader(backupSocket.getInputStream()));
                
                newWriter.println(peer.getName());
                do{
                    /* Tell target node how much items you have*/
                    newWriter.println(data.count());
                    /* Give the target it's old stuff back */
                    newWriter.println(data.getAll());
                } while(!newReader.readLine().trim().equals("SUCCESS"));
                newWriter.println("YOUR STUFF");
                archivalMap.remove(name);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public boolean retrievedData(){
        return  retrieved;
    }
    public void run(){

        try (ServerSocket hostSocket = new ServerSocket(socketNumber)) {
            /* cap the amount of maximum backupMap */
            var pool = Executors.newFixedThreadPool(maxClients);
            /* Stay on to lookout for more clients - essentially an infinite loop */
            while (!Thread.interrupted())
                pool.execute(new Backups(hostSocket.accept()));
        } catch (IOException e) {
            System.out.println("Socket could not be created, check processes permissions");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Something went wrong :(");
            e.printStackTrace();
        }
    }
    /* inform all nodes about change in back up node so they can change their next in line - ** MUST CATCH IN KVSNetworok API ** */ 
    public void informBackupChanged(String newBackupInfo) {
        for( var p: backupWriter){
            p.println("BACKUP CHANGE");
            p.println(newBackupInfo);
        }
    }

    private class Backups implements Runnable {

        private final Socket socket;
        private PrintStream p;
        private BufferedReader r;
        private KVSNetworkAPI backupKVS;
        private String name="FAILED";
        Backups(Socket socket) {
            this.socket = socket;
            backupKVS = new KVSNetworkAPI();
        }

        @Override
        public void run() {
            try {
                /*System.out.println("New Backup" + socket);*/
                p = new PrintStream(socket.getOutputStream());
                r = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                KVSCommandObject command;
                this.greetingExchange();

                /* loop while backupMap is active */
                do {
                    var json = r.readLine();
                    // System.out.println("JSON"+json);
                    try{
                        command = gson.fromJson(json, KVSCommandObject.class);
                    }catch (NullPointerException e){
//                        e.printStackTrace();
                        break;
                    }

                }while(backupMap.containsKey(name) && this.runCommand(command.command, command.key, command.payload) );
            } catch (IOException e) {
//                e.printStackTrace();
            /* necessary because we don't want a crash on one backupMap to break the program */
            } catch (Exception e) {
                System.out.println("Error:" + socket);
                e.printStackTrace();
            } finally {
                if(backupWriter.contains(p)){
                    backupWriter.remove(p);
                }
                if(backupMap.containsKey(name)){
                    backupMap.remove(name);
                    if(!name.equals("FAILED")){
                        archivalMap.put(name,this.backupKVS);
                        System.out.println("added archive");
                    }
                }
                try {socket.close();
                } catch (IOException e) { e.printStackTrace();}
                System.out.println("Node " + name + " died");
            }
        }

        private void greetingExchange() throws IOException {

            /* Ask for old stuff - if leader can find they will connect with the other back up and get*/
            var response = "FAIL";
            name = r.readLine();
            /* Add backup to map */ 
            backupMap.put(name, this.backupKVS);
            HashMap<String, Object> values = null;
            while(response.equals("FAIL")){
                /* Get count */
                int count = Integer.parseInt(r.readLine().trim());
                Type collectionType = new TypeToken<HashMap<String, Object>>(){}.getType();
                var json = r.readLine().trim();
                // System.out.println("JSON received"+json);
                if(json.length()>0){
                    values= gson.fromJson(json, collectionType);
                }
               
                if(values.size() == count){
                    response = "SUCCESS";
                }
                backupKVS.addAll(values);
                p.println(response);
            }
            var status = r.readLine().trim();
            switch (status){
                case "LEADER":
                 /* If, the node reaching out to you is the leader node get info for your */
                    var ip = r.readLine();
                    // System.out.println("ip"+ ip);
                    var port = Integer.parseInt(r.readLine().trim());
                    // System.out.println(port);
                    peer.setBackup(ip);
                    backupWriter.add(p);
                    break;
                case "YOUR STUFF":
                    /* Get rid of the backup because the nice node was only giving you your stuff back */
                    backupMap.remove(name);
                    name="FAILED";
                    peer.addOldData(values);
                    retrieved = true;
                    break;
                default: /* case "NORMAL": */
                    /* In default case the last back up node just died so give them your next back up node as their next in line*/
                    p.println(backupKVS.getBackupIP());
                    p.println(backupKVS.getBackupPort());
                    backupWriter.add(p);
            }            
        }

        public boolean runCommand(String command, String key, String payload) {
            // System.out.println("GOT COMMAND "+ command);
            // System.out.println("key "+ key);
            // System.out.println("Payload "+ payload);
            if (command.equals("insert") || command.equals("update") || command.equals("upsert")) {
                switch (command) {
                    case "insert":
                        backupKVS.insert(key, payload);
                        break;
                    case "update":
                        backupKVS.update(key, payload);
                        break;
                    default: // upsert
                        backupKVS.upSert(key, payload);
                }
            }
            else if (command.equals("get") || command.equals("delete") || command.equals("find")) {
                switch (command) {
                    case "get":
                        backupKVS.get(key);
                        break;
                    case "delete":
                        backupKVS.delete(key);
                        break;
                    //left value as boolean to make it easier for backupMap to use
                    default: // for find
                        backupKVS.find(key);
                }
            } 
            else //commands with no parameter
                switch (command) {
                    case "clear":
                        backupKVS.clear();
                        break;
                    case "count":
                        backupKVS.count();
                        break;
                    case "get_all":
                        backupKVS.getAll();
                        break;
                    case "relieve": 
                    backupMap.remove(name);
                    default:
                        System.err.println("COMMAND:" + command +"EXITING LOOP");
                        return false;
                }

            return true;
        }

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
}