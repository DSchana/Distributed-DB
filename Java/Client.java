import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.util.*;
import com.google.gson.*;

public class Client {
    public static Gson gson;
    private static String  input,output; 
    private static Scanner sc;
    public static void main(String args[]) throws UnknownHostException, IOException
    {
        if (args.length != 1) {
            System.err.println("Pass the server IP as the sole command line argument");
            return;
        }
        gson = new GsonBuilder().create();
        sc = new Scanner(System.in);
        runClient(args[0]);  
    } 
    
    /* Main program for client - connect to server */
    private static void runClient(String socketIP){
        int command = 8 ;  // hold clients command
        String[] commandList ={"insert","update","upSert", "get", "delete", "find", "clear", "count"};
        String serverResponse; // hold server response 

        // Connect to a server using the IP address and port #
        try (Socket serverSocket = new Socket(InetAddress.getByName(socketIP),Server.SOCKET_NUMBER)) {
            //Link to server
            PrintStream p = new PrintStream(serverSocket.getOutputStream());
            Scanner sc1 = new Scanner(serverSocket.getInputStream());
            while(serverSocket.getInetAddress().isReachable(10)){
                if(command <9){
                    // unfortunately Java's scanner is horrible with detecting input when used over a socket
                    while(sc1.hasNextLine() ){ 
                        serverResponse = sc1.nextLine();
                        if(serverResponse.trim().equals("END"))
                            break;
                        System.out.println(serverResponse);
                    }           
                }
                userPrompt();
                command = sc.nextInt(); //if user entered number then get
                sc.nextLine(); // clear line 
                if(command == 0){ // quit
                    break;
                }
                else if(command <7){
                    System.out.println("Enter all the argument you want to send and enter SEND to send");
                    if(command < 4) //Handle all the one input stuff
                        output = twoInput();
                    else//Handle the two argument commands
                        output = oneInput();
                }
                if(command < 9){
                    p.println(commandList[command-1]);
                    System.out.println(commandList[command-1]);
                    p.println(output);
                    p.println("END");
                    System.out.println(output);
                    output = "";
                }
            }
            sc.close();
            System.out.println("Successfully exited client");
        }catch (IOException  e) {
            System.out.println("Socket could not be created, check processes permissions");
            System.err.println(e);
        }
    }

    /* prompt for the client */
    private static void userPrompt(){
        System.out.println("Select a command to enter or 0 to quit");
        System.out.println("1.Insert - Create a new key-value pairs");
        System.out.println("2.Update - update key value pairs");
        System.out.println("3.UpSert - update the key value pair or insert if it doesn't exist");
        System.out.println("4.Get - retrieve the values of the corresponding keys");
        System.out.println("5.Delete - remove the values of the corresponding keys");
        System.out.println("6.Find - Check if keys exist in store");
        System.out.println("7.Clear - Empty out the store");
        System.out.println("8.Count - get the size of the key-value store");
    }

    /* Handle the functions with one arguments, used ArrayDeque instead a linked list because it is more efficient*/
    private static String oneInput(){
        Deque<String> keys = new ArrayDeque<String>();
        System.out.println("Enter the list of keys");
        while(sc.hasNextLine()){
            input = sc.nextLine();
            if(input.equals("SEND"))
                break;
            keys.add(input); // ** If it was a different type I could potentially cast it*
        }
        return gson.toJson(keys);
    }

    private static String twoInput(){
        Map<String, String> pairs = new HashMap<String, String>();
        System.out.println("Enter the list of key value pairs");
        System.out.println("Separate them with a comma (e.g. key, value)");
        while(sc.hasNextLine()){
            input = sc.nextLine();
            if(input.equals("SEND"))
                break;
            String[] argument = input.split(",");
            pairs.put(argument[0],argument[1]); 
        }
        return gson.toJson(pairs);
    }

    /*
    private static class ServerInputHandler extends Thread {
        private Socket serverSocket;
        private Scanner sc1;
        
        ServerInputHandler(Socket serverSocket) {
            this.serverSocket = serverSocket;
        }
        @Override
        public void run() {
            try{ 
                sc1 = new Scanner(serverSocket.getInputStream());
                while(!this.isInterrupted()){
                    serverCanPrint.lazySet(sc1.hasNextLine()); //signal to main thread that server want to send message
                    // && allowServerPrint.get()
                    if(sc1.hasNextLine() ) // Main thread will let server know when it is safe to print
                        System.out.println(sc1.nextLine());    
                }
            }catch (IOException e){
                System.err.println(e);
            } 
        }
    }*/

}
