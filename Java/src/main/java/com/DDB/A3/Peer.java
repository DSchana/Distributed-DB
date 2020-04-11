package main.java.com.DDB.A3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/* Level is used to hold the current level of the node */
enum Level {
  UNNAMED_MEMBER,
  FOLLOWER_LEVEL,
  CANDIDATE_LEVEL,
  LEADER_LEVEL
}
public class Peer implements Runnable{
    /* Object used JSON conversion */
    public final Gson gson;

    /* Key Value store - used top store data */
    private final KVSNetworkAPI kvs;

    /*  Report node is used to send keep alive signal 
    *   For candidate nodes it is also where they receive a list of followers to update their list
    */
    public final ReportNode reportNode; // send keep alive signal to this node

    /*  list of nodes that node is keeping track of - it will be empty for regular nodes
    *   For candidate nodes the nodes won't be active
    */
    public final LinkedHashSet<FollowerNode> followers;
    
    /* Enum variable used to track state of the node*/
    public Level level; 

    /* Group chat handles the multi-cast functionality of the program 
    *   it will be used to accept nodes by leaders
    *   it will also be used to ensure uniqueness of name 
    *   it is also where I plan on implementing remote calls to the KVS store ** UNIMPLMENTED **
    */
    public GroupChat gc;

    /* The number of maximum follower a node can have */
    public final int MAX_FOLLOWERS = 3; // ** TESTING NORMALLY 25 **

    /* node refers to the candidate node */
    public FollowerNode candidate;

    /* Used to keep track of updates to the list */
    private final Set<String> addQueue;
    private final Set<String> deleteQueue;

    /*  ** Used to ensure data isn't loss from KVS on node failure ** */
    // public **some data structure or just String if one Node ** friendNode;
    

    public static void main(String[] args) throws IOException{
        Peer p = new Peer();
        p.run();
    }

    public Peer() throws  IOException{
        reportNode = new ReportNode(this);
        kvs = new KVSNetworkAPI();
        gson = new GsonBuilder().setPrettyPrinting().create();
        followers = new LinkedHashSet<>();
        addQueue = new HashSet<>();
        deleteQueue = new HashSet<>();
        FollowerNode.staticInitialize(this);
        gc = new GroupChat("224.0.0.1", 80, this);
        level = Level.UNNAMED_MEMBER; // start of as unnamed member
    }
    
    /* start all the needed thread then ** start the local KVS manipulation program ** */
    public void run(){
        // Start listening at its port for a node to become leader and if found then start reporting
        new Thread(reportNode).start();
        new Thread(gc).start(); // start listening to appropriate group chats
        Scanner sc = new Scanner(System.in);
        // first get name - ** probably change to random 64 bit string **
        String name;
        try {
            do{
                System.out.println("Please choose a username");
                name = sc.nextLine();
                System.out.println("Checking for username uniqueness");
            } while(!gc.checkName(name));
            System.out.println("Username accepted as unique");
            gc.setFollowerLevel();
            joinNetwork(); // node will try to join an existing network
            System.out.println("Past joining");
//            System.out.println(gc.checkName(name));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // ** new thread allow for manipulation of KVS store locally like old client program **

        // Exit stuff --> put in finally
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Handle node state */
    /* Become the leader node - let group chat, report node and follower nodes know*/
    public void setLeaderMode(){
        /* Start checking follower health */
        for(FollowerNode f : followers)
            f.setCheckBeat(true);

        /* start candidate managing thread */
        new Thread(this::manageCandidateNodes).start();

        /* Tell group-chat we have become a leader so that it can start looking for new nodes */
        gc.setLeaderLevel();

        /* Make the report node the leader */
        reportNode.setLeader(true);
    }

    public boolean isLeader(){
        return (level == Level.LEADER_LEVEL);
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Handle follower Node interaction with peer node */
    // public boolean needCandidate(){
    //     return (level.get() == LEADER_LEVEL) && !(reportNode.foundNode());
    // }

    // remove a node considered dead - self-reported by dead node 
    public synchronized void removeNode(FollowerNode f){
        if(f==null) {
            System.err.println("ERROR: Tried deleting null follower node");
        }
        else{
            followers.remove(f);
            /* Send update to follower if leader*/
            if(this.isLeader() && reportNode.foundNode()){
                deleteQueue.add(f.getIp());
            }
        }
    }

    /* used by candidate - when leader tells it that a node is dead */
    public void removeNode(String ip){
        this.removeNode(new FollowerNode(ip));
    }

    public void removeAllNodes(String json){
        String[] followerNodes = gson.fromJson(json, String[].class);
        for(String f : followerNodes){
            this.removeNode(f);
        }
    }

    /* Add function from the message received in multi-cast channel
    * The reason it is synchronized is if we are waiting for multiple followers
    * and we don't want to ask them all to be our candidate - so, we block 
    */
    public synchronized void addNode(Socket reportSocket) throws IOException {
        FollowerNode f = new FollowerNode(reportSocket);
        new Thread(f).start();
        followers.add(f);
        if(this.isLeader()){
            f.setCheckBeat(true);
        }
        /* Send update to follower */
        if(this.isLeader() && reportNode.foundNode()){
            addQueue.add(reportSocket.getInetAddress().getHostAddress());
        }
        if(followers.size()>=MAX_FOLLOWERS){
           this.delegateToNewLeader();
        }
    }

    public void addNode(String ip){
        FollowerNode f = new FollowerNode(ip);
        new Thread(f).start();
        followers.add(f);
    }

    public void addAllNodes(String json){
        String[] followerNodes = gson.fromJson(json, String[].class);
        for(String f : followerNodes){
            this.addNode(f);
        }
    }

    /* used to create a new leader when the node is full */
    private synchronized void delegateToNewLeader(){
        Iterator<FollowerNode> iterator = followers.iterator();
        ArrayDeque<FollowerNode> removeNodes = new ArrayDeque<>();
        ArrayDeque<String> ipAddressToRemove = new ArrayDeque<>();
        FollowerNode removeNode;
        int numElementsRemoved = followers.size()/2; // remove half the elements
        while(numElementsRemoved >0 && iterator.hasNext()){
            removeNode = iterator.next();
            if(removeNode == candidate){ // don't want to remove candidate
                continue;
            }
            removeNodes.add(removeNode);
            ipAddressToRemove.add(removeNode.getIp());
            numElementsRemoved--;
        }
        for(FollowerNode newLeader: removeNodes){
            /* Don't send the new leader their own IP */
            ipAddressToRemove.remove(newLeader.getIp());
            newLeader.initiateLeaderElection(gson.toJson(ipAddressToRemove));
            if(newLeader.isLeader()) // if node became leader then we are done
                break;
            ipAddressToRemove.add(newLeader.getIp());
        }

        /* Send good bye */
        for(FollowerNode transferNode: removeNodes){
            transferNode.killNode();
        }       
    }

    
    public String getAddQueue(){
        /* if added got deleted then don't force node to add it then delete it */
        addQueue.removeAll(deleteQueue);
        String response = (addQueue.size()==0) ? "" : gson.toJson(addQueue);
        /* clear queue after processing */
        addQueue.clear(); 
        return response;
    }

    public String getDeleteQueue(){
        String response = (deleteQueue.size()==0) ? "" : gson.toJson(deleteQueue);
        /* clear queue after processing */
        deleteQueue.clear(); 
        return response;
    }

    /* start thread to look for a possible candidate 
    *  triggered when node becomes leader or by report node when candidate node dies
    */
    public void findCandidate(){
        new Thread(this::manageCandidateNodes).start();
    }
    
    public void setCandidate(FollowerNode f){
        reportNode.setCandidate(f.getSocket());
        candidate = f;
        /* Create list of followers to send to follower */
        for(FollowerNode f2 : followers){ 
            if(f == f2){ // don't add follower to own list
                continue;
            }
            addQueue.add(f2.getIp());
        }
    }

    public void candidateDied(){
        candidate = null;
        this.findCandidate();
    }

    /* function to find and replacing candidate nodes -> It is run by leader node upon becoming a leader */
    private void manageCandidateNodes (){
        while(!reportNode.foundNode()){
            for( FollowerNode f : followers){
                f.initiateCandidateElection();
                if(f.isCandidate()){
                    break;
                }
            }
        }
        
    }

    // * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * //
    /* Handle group chat interaction */
    /* Get the KVS store used by node - used by group chat to allow remote manipulation */
    public KVSNetworkAPI getKVSAPI(){
        return kvs;
    }

    /* function to join -  send message to join - node with fewest follower will become the leader of the new node
    *  For founder node - the first follower will become the report node and candidate node
    *  Returns true if it joined a network
    */
    public void joinNetwork() throws IOException {
        var ipSet = IpFinder.findIP();
        GroupChat.Command message = gc.getJoinMessage(ipSet); // gets serialized version of join message
        int tries=3; // ## get from setting ##
        System.out.println("Attempting to join network please wait...");
        for(int i=1; i<=tries; i++){
            gc.sendCommand(message);
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

  

}
