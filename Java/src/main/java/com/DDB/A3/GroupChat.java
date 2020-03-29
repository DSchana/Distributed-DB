// Abdullah Arif
// Class representing different "multi-cast channels" which will handle various things
package main.java.com.DDB.A3;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

// imports
public class GroupChat implements Runnable {
    protected final MulticastSocket socket;
    protected final InetAddress group;
    private static Gson gson =  new GsonBuilder().setPrettyPrinting().create(); // Object used JSON conversion
    private KVSNetworkAPI kvsAPI;
    private Peer peer;
    private String name;
    private DatagramSocket writeSocket;
    public static final int UNNAMED_MEMBER =-1, FOLLOWER_LEVEL=0, CANDIDATE_LEVEL=1, LEADER_LEVEL=2;
    private AtomicInteger level;

    /* Atomic boolean to control privileges 
    leader level: allows nodes to take in nodes - only used by leaders
    leaderList: allows node to update list of leaders - used by leader and candidates ** unimplemented as the group chat works **
    nameChat: ensures that the name used by a node is unique - used by all nodes
    */
    public GroupChat(String multicastAddress, int multicastPort, Peer peer) throws IOException {
        this.peer = peer;
        kvsAPI = peer.getKVSAPI();
        socket = new MulticastSocket(multicastPort);
        group = InetAddress.getByName(multicastAddress);
        if(!group.isMulticastAddress()){
           throw new IOException("ERROR: the set IP address does not support multi-casting"); 
        }
        writeSocket = new DatagramSocket();
        socket.joinGroup(group);
        level = new AtomicInteger(UNNAMED_MEMBER);
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Handle state of Group-chat */
    // return true if function currently does name have an accepted name
    public boolean nameless(){
        return this.name == null || this.name.equals("");
    }

    // Set group chat level to follower
    public void setFollowerLevel(){
        level.lazySet(FOLLOWER_LEVEL);
    } 

    // Set group chat level to Leader --> Removed candidate because it does not matter to group chat
    public void setLeaderLevel(){
        level.lazySet(LEADER_LEVEL);
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Main loop - used to handle the various multi-cast channel*/
    public void run() {
        byte[] buf; // array where info is received
        DatagramPacket packet; // create container to receive packet
        String received;
        try {
            while (!Thread.interrupted()) {
                buf = new byte[256];
                packet = new DatagramPacket(buf, buf.length); 
                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                received = new String( packet.getData(), 0, packet.getLength() );
                this.processData(received);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(group != null){
                try {
                    socket.leaveGroup(group);
                    writeSocket.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } 
    }

    /* Used to internally process data received through the channels*/ 
    private synchronized void processData(String json) throws IOException {
        // deserialize message from JSON
        Command message = GroupChat.gson.fromJson(json, Command.class);
        String command = message.command;
        
        switch(level.get()){
            case UNNAMED_MEMBER: // separated because we don't want legit nodes deleting their own name
                if (command.equals("DENY NAME"))
                    if(message.name.equals(this.name))
                        this.name = "";
                break;
             // Check if message can be handled at Leader level if node is a leader
            case LEADER_LEVEL: 
                // updateLeaderList();
                if (command.equals("GREETINGS")) {// need name to add and payload is IP address
                    // assuming name hasn't been taken - if it has it might mess up the kvs calls
                    acceptNode(gson.fromJson(message.payload, String[].class));
                }
            // INTENTIONAL FALL THROUGH HERE!
            // Check if message can be handle at candidate if node is at-least a candidate
            // case CANDIDATE_LEVEL:
            //     switch (command) {
            //         case "NEW":
            //             System.err.println("ERROR: Trying to add leader. Already have shared leader chat");
            //             break;
            //         case "DEAD":
            //             System.err.println("ERROR: Trying to delete leader. Already have shared leader chat");
            //             break;
            //     }       
            // INTENTIONAL FALL THROUGH HERE! 
            default: // Lastly, check if message can be handled at follower level
                switch (command) {
                    case "NAME": //checkName
                    if (message.name.equals(this.name))
                        this.sendCommand("DENY NAME");
                        break;
                    case "CONNECT": //  ** will be used for next part to create one on one connection for RPC calls ** 
                        System.err.println("ERROR: Trying to make a remote call to KVS. Implemented for next assignment");
                        break;
                }
        }
    }

    /* Using the list of ip addresses from the greeting  */
    private void acceptNode(String[] ips) {
        for(String ip : ips){
            Socket s = new Socket();
            try {
                InetAddress address = InetAddress.getByName(ip);
                // 30 seconds wait to accept
                s.connect( new InetSocketAddress(address, FollowerNode.socketNumber), 30000 );
                peer.addNode(s); // if successful then add node
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Handle sending various remote commands to group chat */
    /* Send network command */
    public void sendCommand(Command command) throws IOException {
        String message = gson.toJson(command);
        System.out.println("NODE " + name + " send out " + message);
        // send command to group chat 
        byte[] buf = message.getBytes(); // Send the serialized command
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, 80);
        writeSocket.send(packet);
    }

    /* Send remote commands using a payload */
    public void sendCommand(String commandName, Object payload) throws IOException{
        Command command = new Command(commandName, this.name, gson.toJson(payload));
        this.sendCommand(command);
    }

    /* Send remote commands with no arguments */
    public void sendCommand(String commandName) throws IOException{
         Command command = new Command(commandName, this.name);
         this.sendCommand(command);
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Specific commands used for joining*/
    /* Ask if name can be used */
    public boolean checkName(String name) throws IOException {
        Command command = new Command("NAME", name);
        this.sendCommand(command);
        this.name = name;
        // Wait for someone to deny you a name
        try {
            Thread.sleep(20000); // wait 20 seconds for a reply ** May change to 30 depending on the other timers **
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (this.name.equals("")) // If name was reset it means it was denied
            return false;
        // if name is unique return true and set privilege level
        // peer.becomeFollower();
        return true;
    }
 
    // creates join message depending on the IP
    public Command getJoinMessage(Object[] ipSet){
        String message = GroupChat.gson.toJson(ipSet);
        return new Command("GREETINGS", this.name, message);
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Class used to quickly serialize commands before sending */
    public static class Command{
        public String command, name, payload;
        Command(String command, String name, String payload){
            this.command = command;
            this.name = name;
            this.payload = payload;
        }

        Command(String command, String name){
            this.command = command;
            this.name = name;
            payload = null;
        }
    }
}

