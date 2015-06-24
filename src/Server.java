import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class Server extends Thread {
  // multicast IP address
  private static String serverIP = null;
  // port that server is listening to (2333 is default)
  private static int serverPort = 2333;
  
  //flag to notify that server should keep working
  static boolean keepWorking = true;
  
  // flag to show that we are currently in syncronization round
  static boolean keepSynchronizing = false;
  
  // current round ID
  int roundID;
  // beginning of the round
  long roundStart;
  // round duration in milliseconds (default 5 seconds)
  int roundDuration = Node.syncDuration;
  
  // byte arrays to store data that is sent and received
  byte[] receiveData = null;
  byte[] sendData = null;
  
  //to store received packets
  DatagramPacket receivePacket = null;
  
  // to store received time message
  TimeMessage msg = null;
  
  // socket to send data to and read from
  DatagramSocket serverSocket = null;  
  
  String serverUUID;
  
  // difference that was not corrected yet
  //int timeCorrection;
  //int correctionRoundID;
  
  public Server(int port) {
    serverUUID = UUID.randomUUID().toString();
    
    // UDP mode, multiple IPs and ports for the clients, one port for the server
    setPort(port);
    
    receiveData = new byte[1024]; //bytes
    sendData = new byte[1024]; //bytes
  }
  
  public Server(String ip, int port) {
    serverUUID = UUID.randomUUID().toString();
    
    // Multicast mode, single IP and port for the server and the clients
    setIp(ip);
    setPort(port);

    receiveData = new byte[1024]; //bytes
    sendData = new byte[1024]; //bytes
  }
  
  public void connect() throws SocketException {
    serverSocket = new DatagramSocket(getPort());
    // set socket timeout to 1 second that we can stop thread safely after all
    serverSocket.setSoTimeout(1000);
  }
  
  static void log(String messsage) {
    Node.log(messsage);
  }
  
  // separate thread to process syncronization rounds
  public void run() {    
    receivePacket = new DatagramPacket(receiveData, receiveData.length);

    while(keepWorking) {
      try {
        //serverSocket.setSoTimeout(1000);
        serverSocket.receive(receivePacket);
    
        msg = TimeMessage.deserialize(receivePacket.getData());
  
        if (msg.roundID < roundID) {
          // if message received is from earlier round
          log("Received message from node " + msg.slaveIP + " from earlier round no. " + msg.roundID + "\n");
          log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
        } else {
          // if it's from the current round (higher round isn't possible because server starts round and finishes it  
          msg.tsA3 = Node.localTimeMillis(true); // return time with correction that not to affect average
          log("Received message from node " + msg.slaveIP + " with UUID " + msg.slaveUUID + "\n");
          log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
        }
        
        // Simulate SocketTimeoutException to check whether we are still in synchronization round or not
        throw new SocketTimeoutException();
      } catch (SocketTimeoutException e) {
        // 1000ms have elapsed but nothing was read
        
        // Check whether we are still in synchronization round or not
        if (keepSynchronizing) {
          // synchronization round should be finished, ignore last received message
          if (roundStart + roundDuration < Node.localTimeMillis()) {
            
            if (Node.slavesResponses.size() > 0) {
              // reserve array to store deltas
              Integer[] deltas = new Integer[Node.slavesResponses.size() + 1];
              // corresponding UUIDs
              String[] UUIDs = new String[Node.slavesResponses.size() + 1];
              
              // first one is the master itself
              deltas[0] = 0;
              UUIDs[0] = serverUUID;         
              
              TimeMessage currentMessage;
              int k = 1;
              
              for (Map.Entry<String, TimeMessage> entry : Node.slavesResponses.entrySet()) {
                UUIDs[k] = entry.getKey();
                currentMessage = entry.getValue();
                deltas[k] = (int) ((currentMessage.tsA1 + currentMessage.tsA3) * 0.5 - currentMessage.tsB2);
                k++;
              }
              
              Integer average = computeNonfaultyAverage(deltas, Node.threshold);
              
              if (average == null) {
                log("All clocks are faulty. Can't compute average.\n");
                log("Please, provide larger threshold in program paremeters.\n");
                log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
              } else {
                // now we can send correction requests to all nodes
                for (k = 0; k < deltas.length; k++) {
                  if (k == 0) {
                    // correction of the server (master) time
                    Node.addTimeCorrection(deltas[0] - average, roundID);
                    //log("Made correction to the server: " + (deltas[0] - average) + " milliseconds\n");
                    //log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
                  } else {
                    // correction of the client (slave) time
                    
                    // get last response message from slave
                    TimeMessage tMsg = Node.slavesResponses.get(UUIDs[k]);
                    
                    // end send correction message to the slaves that gave information about their local time
                    try {
                      sendCorrectionRequest(tMsg, deltas[k] - average);
                    } catch (IOException e2) {
                      throw new RuntimeException(e2);
                    }
                  }
                }
              }
            }
            
            keepSynchronizing = false;
          } else {
            //printAllObjectFields(msg);
            
            if (msg != null) {
              //log(msg.slaveUUID + "\n");
              // save received message
              Node.slavesResponses.put(msg.slaveUUID, msg);
              msg = null;
            }
            // just move to the next iteration of the cycle
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }    
  }
  
  public Integer computeNonfaultyAverage(Integer[] deltas, int threshold) {
    // faulty/nonfaulty mark
    Boolean[] faulty = new Boolean[deltas.length];
        
    int bbCount = 0; // number of clocks with deltas that differs more than specified amount from target clocks
    
    for (int i = 0; i < deltas.length; i++) {
      bbCount = 0;
      for (int j = 0; j < deltas.length; j++) {
        if (Math.abs(deltas[i] - deltas[j]) > threshold) {
          bbCount++;
        }
      }
      // According to the article:
      // A clock is considered faulty if its value is more than a small
      // specified interval away from the values of the clocks of
      // the majority of the other machines.
      faulty[i] = bbCount > Math.floor(deltas.length * 0.5f);
    }
    
    log("Deltas: " + Arrays.deepToString(deltas) + "\n");
    log("Threshold: " + threshold + "\n");
    log("Faulty marks: " + Arrays.deepToString(faulty) + "\n");
    
    // maximum count and total sum of acceptable deltas
    int mCount = 0;
    int mSum = 0;
    
    // average of all deltas including master
    int average = 0;

    // iterate and add only deltas from nonfaulty clock
    for (int k = 0; k < deltas.length; k++) {
      if (!faulty[k]) {
        mCount++;
        mSum += deltas[k];
      }
    }
    log("Nonfaulty: " + mCount + "; Faulty: " + (deltas.length - mCount) + "; Total: " + deltas.length + "\n");
    
    if (mCount > 0) {
      average = mSum / mCount;
      log("Average difference in time is: " + average + "\n");
      return average;
    } else {
      return null;
    }
  }

  public void signalStop() {
    keepWorking = false;
  }

  void startSyncRound() throws IOException {
    if (keepSynchronizing) {
      log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
      log("You can't start next synchronization round before finishing previous one\n");
      //log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
      return;
    }
    
    roundID++;
    roundStart = Node.localTimeMillis();
    keepSynchronizing = true;
    
    log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
    log("New synchronization round no. " + roundID + " started\n");
    //log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
    
    String[] parts = null;
    String slaveIP = null;
    Integer slavePort;
    
    for (String entry : Node.slavesAddresses) {
      parts = entry.split("\\:");
      slaveIP = parts[0];
      slavePort = Integer.parseInt(parts[1].trim());
      
      sendSyncRequest(roundID, slaveIP, slavePort);
    }
  }
  
  byte[] stringToIP(String ip) {
    //log("Once again, IP is: " + ip + "\n");
    String[] parts = ip.split("\\.");
    //log(parts[0] + " . " + parts[1] + " . " + parts[2] + " . " + parts[3] + "\n");
    byte[] IP = new byte[]{
            (byte)Integer.parseInt(parts[0]), 
            (byte)Integer.parseInt(parts[1]), 
            (byte)Integer.parseInt(parts[2]), 
            (byte)Integer.parseInt(parts[3])
           };
    //log("Parsed IP: " + new String(IP) + "\n");
    return IP;
  }
  
  void sendCorrectionRequest(TimeMessage msg, int correction) throws IOException {
    log("Sending correction request to IP: " + msg.slaveIP + " and port: " + msg.slavePort + "\n");
    log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
    
    InetAddress address = InetAddress.getByAddress(stringToIP(msg.slaveIP));
    DatagramPacket packet = null;
    
    msg.setActionID(TimeMessage.ACTION_CORRECT);
    msg.setCorrection(correction);
    
    byte[] sendData = TimeMessage.serialize(msg);

    packet = new DatagramPacket(sendData, sendData.length, address, msg.slavePort);
    
    //serverSocket = new DatagramSocket(); //commented because socket was already created in run() method
    serverSocket.send(packet);
  }

  void sendSyncRequest(int roundID, String ip, int port) throws IOException {
    log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
    log("Sending request to IP: " + ip + " and port: " + port + "\n");
    
    InetAddress address = InetAddress.getByAddress(stringToIP(ip));
    DatagramPacket packet = null;
    
    TimeMessage msg = new TimeMessage();
    msg.setActionID(TimeMessage.ACTION_REQUEST);
    msg.setRoundID(roundID);
    msg.setMasterIP(InetAddress.getLocalHost().getHostAddress());
    msg.setMasterPort(serverPort);
    msg.setTsA1(Node.localTimeMillis(true)); // return time with correction that not to affect average
    
    byte[] sendData = TimeMessage.serialize(msg);

    packet = new DatagramPacket(sendData, sendData.length, address, port);
    
    //serverSocket = new DatagramSocket(); //commented because socket was already created in run() method
    serverSocket.send(packet);
  }

  public static int getPort() {
    return serverPort;
  }

  public static void setPort(int port) {
    Server.serverPort = port;
  }

  public static String getIp() {
    return serverIP;
  }

  public static void setIp(String ip) {
    Server.serverIP = ip;
  }
  
  public void printAllObjectFields(Object obj) {
    for (Field field : msg.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      String name = field.getName();
      Object value;
      try {
        value = field.get(obj);
      } catch (IllegalArgumentException e2) {
        throw new RuntimeException(e2);
      } catch (IllegalAccessException e2) {
        throw new RuntimeException(e2);
      }
      System.out.printf("Field name: %s, Field value: %s%n", name, value);
    }
  }
}
