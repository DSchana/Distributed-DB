/* Abdullah Arif
* COMP-4680
* Program to handle the one on one FollowerNodes that leader nodes have 
* implements all the basic key store operations for followers to use */
package src.main.java.com.DDB.A3;


import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class FollowerNodes {
    // private AtomicBoolean leader; // stores if leader
    private int socketNumber = 100;
    public static final int MISSED_BEATS_THRESHOLD = 2;
    public LinkedHashSet<FollowerNode> followers;
    public AtomicBoolean leader;
    public static FollowerNodes singleton = null;
    

    private FollowerNodes(){
        leader = new AtomicBoolean(false);
        followers = new LinkedHashSet<>();

    }

    public static FollowerNodes getInstance() { 
        if (singleton == null){  
            singleton = new FollowerNodes(); 
            // new Thread(singleton::checkFollower).start();
        }
        return singleton; 
    }

    // default we assume the node is not a leader so this might not be needed ** MIGHT REMOVE  **
    public void setCandidateMode(){
        leader.lazySet(false);
        // Only check first node
        Iterator<FollowerNode> iterator = followers.iterator();
        FollowerNode f = iterator.next();
        if(iterator.hasNext()){// for first node
            f.setCheckBeat(true); 
        }
        while(iterator.hasNext()){
            f = iterator.next();
            f.setCheckBeat(false);
        }
    }

    // Become leader by telling all nodes in your set that you are their new leader ** UNFINISHED **
    public void setLeaderMode(){
         leader.lazySet(true);
        // Wait up to 30 seconds for a response so change SO_TIMEOUT time
        for(Follower f : followers){
            FollowerNode f = iterator.next();
            f.setCheckBeat(true);
        }
    }

    public void delete(FollowerNode f){
        followers.remove(f);
    }

    public void add(String[] ips){
        Socket s;
        for(String ip : ips){
            try (ServerSocket hostSocket = new ServerSocket(socketNumber)) {
            // on successful connection create a thread with with 
        }
        FollowerNode f = new FollowerNode(s, leader.get());
        // If first follower send it a message to add this node as it's follower node (elect it to be candidate need)
    }

    public void add(Socket s){
        FollowerNode f = new FollowerNode(s, true); // will check the beat of this one
        followers.add(f);
    }

    class FollowerNode implements Runnable{
        private Socket followerSocket;
        private BufferedReader reader;
        private PrintStream writer;
        private int missedBeats;
        private FollowerNodes currentSet;
        private AtomicBoolean checkBeat;
        public FollowerNode(Socket s, boolean b){
            missedBeats = 0;
            followerSocket = s;
            writer = new PrintStream(followerSocket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(followerSocket.getInputStream()));
            currentSet = FollowerNodes.getInstance();
            checkBeat = new AtomicBoolean(b);
        }

        // // default
        // public FollowerNode(Socket s){
        //     this(s, false);
        // }

        public void setCheckBeat(boolean b){
            // if we transitioning from leader to candidate or vice-versa we don't want to count old missed beats
            if(b != checkBeat.get()){ 
                this.clearMissedBeat();
            }
            checkBeat.lazySet(b);
        }
        @Override
        public void run(){
            try{
                while(missedBeats<=MISSED_BEATS_THRESHOLD){
                    while(!checkBeat.get()); // if don't have to check the beat we just wait
                        String response = this.getResponse();
                        switch(reponse){
                            case "SWITCH":
                                this.handleSwitch();                                
                                break;
                            case "ALIVE": // If node alive clear the count of the times beat was missed
                                this.clearMissedBeat();
                                break;
                            default: // case "MISSED":
                                this.incrementMissedBeat();   
                        }
                }
            }finally{ // On end close streams and delete from set
                followerSocket.close();
                writer.close();
                reader.close();
                currentSet.delete(this); // Tell main function to consider this dead
            }
        }

        private handleSwitch(){
            if(currentSet.contains(this))
                writer.println("NO");
            else
                writer.println("YES");
        }
        public String getResponse(){
            try {
                String response;
                if ((response = reader.readLine()) != null) {
                    return response;
                } // This should be triggered because the socket set with a time out
            } catch (SocketTimeoutException ignored) { 
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "MISSED";
        }
        // return the total amounts of missed beats
        public int getMissedBeats(){
            return missedBeats;
        }

        // add one to the amount of beats missed
        public void incrementMissedBeat(){
            missedBeats++;
        }

        // reset the missed beat
        public void clearMissedBeat(){
            missedBeats = 0;
        }

        //**********************************************************************************************************
        // We want the linked hash-code to consider nodes with the same IP address and port to be considered the same
        public String getIp(){
            return followerSocket.getRemoteSocketAddress().toString();
        }

        public int getPort(){
            return followerSocket.getPort();
        }

        @Override
        public int hashCode() {
            return this.getIp().hashCode() + this.getPort();
        }

        @Override
        public boolean equals(Object other)
        {
            if (other == null)
                return false;
            if (this.getClass() != other.getClass())
                return false;
            FollowerNode f = (FollowerNode)(other);
            return f.getIp().equals(this.getIp()) && this.getPort() == f.getPort();
        }
    
}