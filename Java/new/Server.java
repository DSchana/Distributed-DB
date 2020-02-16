import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class Server {

    public static final Set<PrintStream> client = new HashSet<>();
    public static Gson gson; // Object used JSON conversion


    public static void main(String[] args) {

        gson = new GsonBuilder().setPrettyPrinting().create();
        int socketNumber = 2000, maxClients = 25;
        try {
            // Open configuration file
            BufferedReader br = new BufferedReader(new FileReader("Server.config"));
            socketNumber = Integer.parseInt(br.readLine().trim());
            maxClients = Integer.parseInt(br.readLine().trim());
            var b = Singleton.getInstance();
            
        } catch (IOException b) {
            System.err.println(
                "Check to make sure Server.config is in directory and server has read permission");
            b.printStackTrace();
        } catch (NumberFormatException b) {
            System.err.println("Make sure the Server.cofig file is in correct format");
            System.err.println("First Line holds socket number: default 2000");
            System.err.println("Second line holds the maximum number of clients: default 25");
            b.printStackTrace();
        }
        Singleton.runServer(socketNumber, maxClients);
    }
}
