// Abdullah Arif
// a runnable object that will be used to find and report to a node
package main.java.com.DDB.A3;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;


public class ReportNode implements Runnable {
	private static final int TIMEOUT = 15000, MISSED_THRESHOLD=3;
	private static final long MOURNING_PERIOD = TIMEOUT *2; // how long it node wait after leader is presumed dead
	private PrintStream reportWriter;
	private BufferedReader reportReader;
	private Peer peer;
	private Socket reportSocket;
	private AtomicBoolean leader;
	public ReportNode(Peer p) {
		reportWriter = null;
		reportSocket = new Socket();
		leader = new AtomicBoolean(false);
		peer = p;
	}

	// Used to find and report to new report Node
	public void run() {
		boolean candidate = false;
		int countMissedBeats = 0 ; // Used to count the times leader failed to response to us
		String response; //hold response from leader

		while (!Thread.interrupted()) {
			// If we have a new report node then we have to wait for socket to be updated
			while(!reportSocket.isConnected() || reportSocket.isClosed()){
				synchronized(reportSocket){
					reportSocket.notifyAll(); /* remove all threads waiting for node*/
					Thread t = new Thread(this::findReportNodes);
					t.start();
					try {
						reportSocket.wait();
						t.interrupt();
						t.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

				}
				
			}
			
			/* if an active candidate */
			if(candidate && !leader.get()){ 
				response = this.getResponse();
				if(response.equals("ADD")){
					/* get follower IP and add follower */
					response = this.getResponse(); 
					peer.addAllNodes(response);
				}
				response = this.getResponse();
				if(response.equals("DELETE")){
					response = this.getResponse(); /* get follower IP */
					peer.removeAllNodes(response); /* delete follower */
				}
			}
			// Handle response
			response = this.getResponse();
			if (response.equals("BYE") || countMissedBeats > MISSED_THRESHOLD) {
				closeConnection();
				if(candidate){
					// Start leader transition after mourning period
					try{
						Thread.sleep(MOURNING_PERIOD);
					} catch (InterruptedException e) {
		                e.printStackTrace();
		            }
		            // become the leader
					peer.setLeaderMode();
					leader.lazySet(true); 
				}

				if(leader.get()){ // if a leader and your report node died that means you no longer have a candidate
					peer.candidateDied();
					candidate = false; // 
				}
			}
			else if (response.equals("LEADER")){
				if(candidate){
					reportWriter.println("NO");
				}
				else{
					leader.lazySet(true); /* you are now set to be a leader on next bye you will eave and become leader */
					reportWriter.println("YES");
					response = this.getResponse();
					peer.addAllNodes(response);
					candidate = true; /* as a candidate the leader will go through a mourning period and become a leader */
				}
			} 
			else if (response.equals("CANDIDATE")){
				try{
					peer.addNode(reportSocket);
					candidate = true;
				} catch (IOException e){
					candidate =false;
				}
				if (candidate)
					reportWriter.println("YES");
				else
					reportWriter.println("NO");
			} 
			else if (response.equals("ALIVE?")){
				reportWriter.println("ALIVE");
			}
			else if(response.equals("MISSED")){
				countMissedBeats++;
			}
			else{
				System.err.println("ERROR: Report node does not know how to handle the message \"" + response +"\"");
			}
		}
	}

	private String getResponse(){
		String response = "BYE";
		try{
			response = reportReader.readLine().trim();
			System.out.println("REPORT NODE RECEIVED: " + response);
		} catch(SocketTimeoutException e){
			e.printStackTrace();
			response = "MISSED";
		} catch (IOException e) {
			e.printStackTrace();
		}
		return response;
	}
	// functions returns true if node has an active node that it reports to
	public boolean foundNode() {
		return reportSocket != null;
	}

	public synchronized void setCandidate(Socket s){
		reportSocket.notifyAll();
		reportSocket = s;
		
	}

	public void setLeader(boolean b){
		leader.lazySet(true);
	}
	// Close the threads safely
	public void close() {
		Thread.currentThread().interrupt();
	}

	// Find or update the node that the current node will send the "heartbeat" signal
	// If for someone reason things get way out of sync even with the built in wait - then allow manual SWITCH
	private void findReportNodes() {
		Socket potentialReport = null;
		System.out.println("Searching for node");
		// while (!Thread.interrupted()) {
		// default 80
		int socketNumber = 80;
		try (ServerSocket hostSocket = new ServerSocket(socketNumber)) {
			hostSocket.setSoTimeout(TIMEOUT); // IO calls should time out after 10 seconds
			while (potentialReport == null && !Thread.interrupted() && !reportSocket.isConnected()) { 
				try {
					potentialReport = hostSocket.accept();
					if(potentialReport != null){
						potentialReport.setSoTimeout(TIMEOUT);
					}
				} catch(SocketTimeoutException e) {potentialReport = null;}
			}
			
			if(potentialReport != null){
				reportSocket.notifyAll(); // unfreeze the main thread
				reportSocket = potentialReport; // replace the socket
				reportWriter = new PrintStream(reportSocket.getOutputStream());
				reportReader = new BufferedReader(new InputStreamReader(reportSocket.getInputStream()));
			}
			
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
		}  catch(SocketException e) {
			e.printStackTrace();
			System.err.println("Will remove this in a bit");
		} catch (IOException e) {
			System.err.println("ERROR: Socket could not be created, check processes permissions");
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.out.println("Closed report node scanner");
		}
		finally{
			reportSocket.notifyAll();
		}
	}

	private void closeConnection() {
		try {
			reportSocket.close();
			reportWriter.close();
			reportReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		reportSocket = new Socket();
	}
}

