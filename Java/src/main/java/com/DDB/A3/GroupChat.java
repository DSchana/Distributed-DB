// Abdullah Arif
// Class representing different "multi-cast channels" which will handle various things
package src.main.java.com.DDB.A3;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

// imports
public class GroupChat implements Runnable {
    protected MulticastSocket socket = null;
    protected byte[] buf = new byte[256];
    protected String multicastAddress;
    protected int multicastPort;
    public static final int JOIN_ROOM=0, LEADER_LIST=1, ROOM_POPULATION=2;
    GroupChat(String ma, int mp) {
        multicastAddress = ma;
        multicastPort =mp;
        // this.socket = socket; // ** Might need **
    }
    public void run() {
        try {
            socket = new MulticastSocket(multicastPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
        InetAddress group = InetAddress.getByName();
        if(!group.isMulticastAddress()){
            System.err.println("ERROR: the set IP address does not support multi-casting")
            return;
        }
        try {
            socket.joinGroup(group);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String received = new String( packet.getData(), 0, packet.getLength() );
            this.processData(received);
        }
        try {
            socket.leaveGroup(group);
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket.close();
    }

    // public void setMulticastAddress(String ma){
    //     NodeScanner.multicastAddress = ma;
    // }

    // public void setMulticastPort(int mp){
    //     System.out.println("Warning:Default port is set to 80. Changing port may cause Node to be disconnected from main group");
    //     NodeScanner.multicastPort = mp;
    // }

    private void processData(String message){
        switch(typeOfChat){
            case LEADER_LIST:
                // updateLeaderList();
                break;
            case ROOM_POPULATION:
                // updateLeastPopulatedRoom();
                break;
            default: // JOIN_ROOM
                acceptNode(message.split(","));
        }
    }

}

// The scanner makes 