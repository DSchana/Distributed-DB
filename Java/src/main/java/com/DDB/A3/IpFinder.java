/* Abdullah Arif
* COMP-4680
* Class to find the IP-address of the server */
package src.main.java.com.DDB.A3;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

public class IpFinder {

    public static String findIP() {
        System.out.println("\nThe client may enter the following IP address to connect\n");
        StringBuilder sb = new StringBuilder();
        try {
            // System.out.println("\nIf client is on the local machine: ");
            // System.out.println(InetAddress.getLocalHost().getHostAddress().trim() + "\n");
            // System.out.println("\nIf client is LAN they may use\n"); / /Assumed to always be on LAN
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                //only get active interface that are nor local
                if (netint.isUp() && !netint.isLoopback() && !netint.isVirtual()) {
                    //get each server interface because machine use multiple interface
                    Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                    for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                        try {
                            if (inetAddress.isSiteLocalAddress() && inetAddress.isReachable(5)) {
                                String s = inetAddress.toString();
                                sb.append(s.substring(1) + ",");
                               
                            }
                        } catch (IOException e) { e.printStackTrace();}
                    }
                }
            }
            // System.out.println("\nIf client outside they may try to use\n");
            // System.out.println(getPublicIP());
        } catch (Exception e) {
            System.out.println("Server info could not be read");
        }
        return sb.toString();
    }

    // We can't use global address if we are trying to multi-cast
    // public static String getPublicIP() {
    //     String ip = "";
    //     try {
    //         String[] ipBots = {"http://checkip.amazonaws.com/", "http://icanhazip.com/",
    //                 "http://www.trackip.net/ip",
    //                 "http://myexternalip.com/raw", "http://ipecho.net/plain",
    //                 "http://bot.whatismyipaddress.com"};
    //         for (String bot : ipBots) {
    //             URL ipBot = new URL(bot);
    //             BufferedReader sc = new BufferedReader(new InputStreamReader(ipBot.openStream()));
    //             ip = sc.readLine().trim();
    //                 if (ip.equals(""))
    //                     break;
    //         }
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     }
    //     return ip;
    // }
}