import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.UUID;

public class Client extends Thread  {
  // multicast IP address
  private static String clientIP = null;
  // port that server is listening to (2333 is default)
  private static int clientPort = 2333;
  
  //flag to notify that client should keep working
  static boolean keepWorking = true;
  
  //byte arrays to store data that is sent and received
  byte[] receiveData = null;
  byte[] sendData = null;
 
  //to store received packets
  DatagramPacket receivePacket = null;
 
  // to store received time message
  TimeMessage msg = null;
 
  // socket to send data to and read from
  DatagramSocket datagramSocket = null;
  
  String clientUUID;
  
  // current correction round ID
  int roundID;
  
  // difference that was not corrected yet
  // int timeCorrection;
  // int correctionRoundID;
  
  public Client(String ip, int port) {
    clientUUID = UUID.randomUUID().toString();
    
    setIp(ip);
    setPort(port);
    
    receiveData = new byte[1024]; //bytes
    sendData = new byte[1024]; //bytes    
  }
  
  public void connect() throws SocketException {
    datagramSocket = new DatagramSocket(getPort());
    // set socket timeout to 1 second that we can stop thread safely after all
    datagramSocket.setSoTimeout(1000);
  }

  public void signalStop() {
    keepWorking = false;
  }

  static void log(String messsage) {
    Node.log(messsage);
  }

  public void run() {
    receivePacket = new DatagramPacket(receiveData, receiveData.length);

    // listening to messages from the server and respond to them
    while(keepWorking) {
      try {
        //serverSocket.setSoTimeout(1000);
        datagramSocket.receive(receivePacket);
    
        msg = TimeMessage.deserialize(receivePacket.getData());
  
        if (msg.actionID == TimeMessage.ACTION_REQUEST) {
          if (msg.roundID <= roundID) {
            // we've received synchronization request from previous round, skip it 
            log("Received message from node " + msg.slaveIP + " from earlier round no. " + msg.roundID + "\n");
            log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
          } else {
            // we've received synchronization request from the new round, process it
            log("Received message from round no. " + msg.roundID + " with synchronization request\n");
            log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");

            // time synchronization response
            sendResponse(msg);
            roundID = msg.roundID;
            
            // we've responded to the syncgronization request from new round
            // we need to clear time correctin left and wait for new correction request
            Node.clearTimeCorrection(0);
          }
        } else if (msg.actionID == TimeMessage.ACTION_CORRECT) {
          if (msg.roundID <= Node.getCorrectionRoundID()) {
            // we've received correction request from previous round, skip it 
            log("Received message from node " + msg.slaveIP + " from earlier round no. " + msg.roundID + "\n");
            log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
          } else {
            // we've received correction request from the new round, process it
            log("Received message from round no. " + msg.roundID + " with correction request\n");
            log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
            
            // make a correction
            Node.addTimeCorrection(msg.correction, msg.roundID);
          }
        } else {
          new RuntimeException(new UncknownActionException());
        }
      } catch (java.net.SocketTimeoutException e) {
        // 1000ms have elapsed but nothing was read
        // just move to the next iteration of the cycle
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public DatagramSocket getSocket() {
    return datagramSocket;
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

  void sendResponse(TimeMessage msg) throws IOException {
    log("Sending response to IP: " + msg.masterIP + " and port: " + msg.masterPort + "\n");
    log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
    
    InetAddress address = InetAddress.getByAddress(stringToIP(msg.masterIP));
    DatagramPacket packet = null;
    
    msg.slaveIP = InetAddress.getLocalHost().getHostAddress();
    msg.slavePort = clientPort;
    msg.slaveUUID = clientUUID;
    msg.tsB2 = Node.localTimeMillis(true); //return time with correction that not to affect average
    byte[] sendData = TimeMessage.serialize(msg);

    packet = new DatagramPacket(sendData, sendData.length, address, msg.masterPort);
    
    //DatagramSocket ds = new DatagramSocket(); //commented because socket was already created in run() method
    datagramSocket.send(packet);
  }

  public void setSocket(DatagramSocket datagramSocket) {
    this.datagramSocket = datagramSocket;
  }

  public static String getIp() {
    return clientIP;
  }

  public static void setIp(String ip) {
    Client.clientIP = ip;
  }

  public static int getPort() {
    return clientPort;
  }

  public static void setPort(int port) {
    Client.clientPort = port;
  }

}
