// Abdullah Arif
// a runnable object that will be used to find and report to a node
package src.main.java.com.DDB.A3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.net.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportNode implements Runnable {
	// how long till it node sends another alive signal
	private static final long SLEEP_TIMER = 5000;
	private static final int TIMEOUT = 10000;
	private int socketNumber; // default 2000
	private PrintStream reportWriter;
	private BufferedReader reportReader;
	// private final ReentrantLock socketLock = new ReentrantLock();  // 
	private AtomicBoolean updatingSocket, allowSocketUpdate, deleteNode;
	private Socket reportSocket;

	public ReportNode(int socketNumber) {
		this.socketNumber = socketNumber;
		updatingSocket = new AtomicBoolean(true);
		allowSocketUpdate = new AtomicBoolean(true);
		deleteNode = new AtomicBoolean(false);
		reportWriter = null;
		reportSocket = null;
	}

	// Used to find and report to new report Node
	public void run() {
		// Run a separate thread to find and update the report node
		Thread[] subThreads = new Thread[2];
		subThreads[0] = new Thread(this::findReportNodes);
		subThreads[1] = new Thread(this::getUpdateFromLeader);
		for(Thread t: subThreads)
			t.start();

		while (!Thread.interrupted()) {
			// If we have a new report node then we have to wait for socket to be updated
			if (updatingSocket.get()) {
				allowSocketUpdate.lazySet(true);
				while (reportSocket == null || updatingSocket.get())
					; // If we are changing the socket then we have to wait
			}
			// Socket is now being used so you stop it from being changed
			allowSocketUpdate.lazySet(false);
			if (deleteNode.get()) {
				closeConnection();
				reportSocket = null;
				deleteNode.set(false);
			} else {
				reportWriter.println("ALIVE");
				try {
					Thread.sleep(SLEEP_TIMER);
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				}
			}
		}

		// Interrupt the thread that finds reports nodes
		for (Thread t : subThreads) {
			t.interrupt(); // Send message to stop
		}

	}

	public boolean foundNode() {
		// If socket is being actively updating on the current call then wait for the update
		if (updatingSocket.get() && allowSocketUpdate.get())
			while (updatingSocket.get()) ;
		return reportSocket == null;

	}

	// Close the threads safely
	public void close() {
		Thread.currentThread().interrupt();
	}

	public void deleteReportNode() {
		deleteNode.set(true);
	}

//	// used by leader to set up their candidate node as their report leader --> Instead will make candidate node send a request to be leader
//	public boolean setReportNode(int ip){
//		// if deleting a report node then wait for thread to finish
//		while(deleteNode.get()){
//			while(!allowSocketUpdate.get()); // if deleting it may create a race-condition
//		}
//		// This function should not be called if we still have a report Node
//		if(reportSocket != null){
//			System.err.println("WARNING: TRIED TO MANUALLY SET IP ADDRESS OF A NON-EMPTY REPORT NODE")
//			return false;
//		}
//		if(!allowSocketUpdate.get()){
//			System.err.println("PERMISSION TO UPDATE SOCKET WAS DENIED");
//		}
//		try (Socket hostSocket = new Socket(InetAddress.getByName(ip), socketNumber)) {
//			hostSocket.setSoTimeout(TIMEOUT); // IO calls should time out after 10 second
//			reportSocket = hostSocket;
//			reportWriter = new PrintStream(reportSocket.getOutputStream());
//			reportReader = new BufferedReader( new InputStreamReader(reportSocket.getInputStream()) );
//		}
//	}

	private void getUpdateFromLeader() {
		while (!Thread.interrupted()) {
			System.out.println("UPDATE FROM LEADER - SHOULD HAPPEN EVERY 10 SECONDS"); // ** DEBUG **
			try {
				while (reportSocket == null || updatingSocket.get())
					; // If we are changing the socket then we have to wait
				String response;
				while ((response = reportReader.readLine()) != null) {
					if (response.equals("DELETE")) {
						deleteReportNode();
						response = "";
					}
					if (response.equals("CANDIDATE")){
						FollowerNodes f = FollowerNodes.getInstance();
						f.add(reportSocket);
					}
				}
			} catch (SocketTimeoutException ignored) { // This should be triggered because the socket set with a time out
				System.out.println("** DELETE FROM ACTUAL CODE - TIME OUT CALL ** ");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Find or update the node that the current node will send the "heartbeat" signal
	private void findReportNodes() {
		Socket potentialReport = null;
		while (!Thread.interrupted()) {
			try (ServerSocket hostSocket = new ServerSocket(socketNumber)) {
				hostSocket.setSoTimeout(TIMEOUT); // IO calls should time out after 10 seconds
				while (!hostSocket.isBound() && !Thread.interrupted()) {
					potentialReport = hostSocket.accept();
				}
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
				updatingSocket.lazySet(true);
				while (!allowSocketUpdate.get()) ; // Thread needs to wait until main thread is done sending messages
				if (getPermissionToSwitch()) { // We don't want node switching if there was miscommunication
					// close up the old streams and sockets
					if (reportSocket != null && !reportSocket.isClosed()) {
						closeConnection();
					}
					reportSocket = potentialReport;
					reportWriter = new PrintStream(reportSocket.getOutputStream());
					reportReader = new BufferedReader(new InputStreamReader(reportSocket.getInputStream()));
				}

				updatingSocket.lazySet(false); // socket has been updated
			} catch (IOException e) {
				System.out.println("Socket could not be created, check processes permissions");
				e.printStackTrace();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.out.println("Closed report node scanner");
				break;
			} catch (Exception e) {
				System.out.println("Something went wrong :(");
				e.printStackTrace();
			}
		}
	}

	// ask to switch and wait for reply for 10 seconds if no reply return true 
	private boolean getPermissionToSwitch() {
		reportWriter.println("SWITCH");
		System.out.println("TRYING TO SWITCH NODES"); // ** DEBUG **
		try {
			String response = reportReader.readLine();
			if (response.equals("NO"))
				return false;
		} catch (SocketTimeoutException e) { // This should be triggered because the socket set with a time out
			System.out.println("Leader is presumed dead");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	private void closeConnection() {
		try {
			reportSocket.close();
			reportWriter.close();
			reportReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}

