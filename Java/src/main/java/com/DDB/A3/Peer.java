package src.main.java.com.DDB.A3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Peer implements Runnable{
    // ** We can keep tracker node in linked list as we will iterate through them **
    /* Tracking nodes
        Keep track of candidate nodes, all joined nodes and all other leaders
     */
    public Gson gson; // Object used JSON conversion
    private KeyValueStore<String, Object> kvs;  // Key Value store
    private AtomicBoolean metaLock; // controls Access to key-value store
    public ReportNode reportNode; // location of node that is waiting for the keep alive signal
    // list of nodes that node is keeping track of - it will be empty for regular nodes
    public LinkedList<FollowerNodes> followerNodes; // For candidate nodes it will always have one element in it
    public Set<String> leaderNodes; // list of all leader nodes
    public int storeAccessSocket; // Socket used for manipulation of the local key value store
    // Used to figure out appropriate response time depending on node size
    public AtomicInteger leastPopulatedNodeSize;
    /*
     If the previous node size was equivalent to leastPopulatedNodeSize the node will propose raising
     - if a node has a lower number it will shout it to the group chat and the lowest proposed number will be the least populated node

     Buddy nodes hold each others data so if one fails the system does not lose data
     // public **some data structure or just String if one Node ** friendNode;
    */

    public static void main(String[] args){
        Peer p = new Peer();
        p.run();
    }
    public Peer(){
        reportNode = new ReportNode(2000);
        kvs = new KeyValueStore<>();
        metaLock = new AtomicBoolean(false);
        leastPopulatedNodeSize = new AtomicInteger(0);
        gson = new GsonBuilder().setPrettyPrinting().create();
    }
    public void run(){
        // Start listening at its port for a node to become leader and if found then start reporting
        new Thread(reportNode);
//        new Thread();

        joinNetwork(); // node will try to join an existing network
//        if node gets report node it is part of a group - implemented below
        while(!Thread.interrupted()){
            Check if followers are alive // can do this for all of them
            IF followers died remove from list // part of check follower function

            if(!reportNode.foundNode()){ // founder
                broadcast Arcibo message; group chat
//                First node to join make report node in report Node
            }
            if(leader){ // if report node = first node in follower list
                Check group chat to see if new node joined; // do in thread
                Check another group chat to update number; // In thread that calls update number
                Every time a node asks you if it is okay to have new leader you say nah; // in follower node
                If you are full make your second node new leader and let the new half know they got new leader
                If your candidate does not want to be candidate move it to back of list and make next node candidate and let candidate know

            }
            if(candidate){ // if report node not in followerNodes and followerNodes not empty then candidate
                If leader dead become new leader and make your first node your report node;
                Listen to leader to update your follower list; // Then you guys switch list size - your list should be one less then leader
                If you do not want to be candidate let leader know - if they accept then you delete your follower list
            }
            if(follower){ // if you don't have any followers
                // separate thread runs as soon as you become a follower - end when you become candidate or leader
                Always be lsitening for a node to connect with you and declare themselves your new leader;
                listen to leader if they tell you to switch leaders or become candidate; // in new thread
                /* if a new node want to be your leader send you leader a request to change if no response then
                 switch leader (leader dead).
                 If leader tells you to switch leaders you change leaders
                 */
            }
        }
        // Exit stuff --> put in finally
    }
    /* function to join -  send message to join - node with fewest follower will become the leader of the new node
    * For founder node - the first follower will become the report node and candidate node
    * Returns true if it joined a network
    */
    public void joinNetwork(){
        // ## Get group 1 in from setting from setting ##
        // Get Ip address from class and use that as message
        String message = IpFinder.findIP();
        int tries=3; // ## get from setting ##
        for(int i=1; i<=tries; i++){
            try {
                sendJoinPrompt(message, "230.0.0.0", 80); // ## Need to get from setting file ##
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            // Wait set out amount - while other listening in ReportNode program
            try {
                if (reportNode.foundNode()) // Don't want to go to sleep if we have already found a node
                    throw new InterruptedException();
                Thread.sleep(1500 * i); // slowly backs off - to give nodes time to process things as needed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Assumed network was joined");
                break;
            }
        }
    }

    /* Function used to send the list of IP address to nodes listening for it*/
    private void sendJoinPrompt(String message, String multicastAddress, int multicastPort) throws IOException {
        DatagramSocket socket= new DatagramSocket();
        InetAddress group = InetAddress.getByName(multicastAddress);
        byte[] buf = message.getBytes(); // Send ip address
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, multicastPort);
        socket.send(packet);
        socket.close();
    }

    /*
    * On exit update setting file if possible - If leader node then tell candidate node that it has become leader node
    */

    /*
    * If node is candidate node and its leader is presumed dead
    * - send message to all follower node that you are their new reportNode node
    */

    /* Function to change report node */

    /* If server full - tell second oldest node to take over as leader - until it gets first follower it report to node*/

    /* Function to become a leader node - tell all leader node, then start scanning for dead follower (if any)
     - and new nodes
    */

    /* Upon new node joining - if this the first node in follower node then make your reportNode */



}
