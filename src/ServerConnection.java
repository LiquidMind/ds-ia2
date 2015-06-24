import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;


public class ServerConnection extends Thread {
  // variable to store opened client socket
  Socket socket = null;
  // socket output and input streams
  DataOutputStream out = null; 
  BufferedReader in = null;
  // to notify when client connection is closed and we need to stop thread
  boolean keepWorking = true;
  
  public ServerConnection(Socket socket) {
    // run constructor of parent class
    super("ServerConnection");
    // save client socket
    setSocket(socket);
  }

  public void run(){
    //Process client connection
   
    try {
      // get socket output and input streams
      out = new DataOutputStream(socket.getOutputStream());
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    } catch (IOException e) {
      new RuntimeException(e);
    }
    
    String message = "";
    
    // listening to messages from the client and send them to any other client
    while(keepWorking){
      try {
        // set socket timeout to 1 second that we can stop thread safely after all
        socket.setSoTimeout(1000);
        try {
          message = in.readLine();
          if (message == null) {
            throw new IOException();
          }
          System.out.print("Message from " + hashCode() + ": " + message + "\n>> ");
          // send received message to every other client
          Server.multicastMessage(this, message);
        } catch (java.net.SocketTimeoutException e) {
          // 1000ms have elapsed but nothing was read
          // just move to the next iteration of the cycle
        }
      } catch (IOException e) {
        //new RuntimeException(e);
        System.out.println("Client " + hashCode() + " was disconnected");
        System.out.print(">> ");
        keepWorking = false;
        //Server.keepWorking = false;
      }
    }
    
    try {
      socket.close();
    } catch (IOException e) {
      //new RuntimeException(e);
      System.out.println("Socket was already closed");
    }
    
    // Remove client from the server's HashSet 
    Server.removeClient(this);    
  }
  
  // send message to this client
  void sendMessage(String message) {
    try {
      out.writeBytes(message + '\n');
    } catch (IOException e) {
      // probably client was shutted down or link is broken
      // notify to stop thread and close connection
      System.out.println("Client was disconnected");
      keepWorking = false;      
    }
  }

  public Socket getSocket() {
    return socket;
  }

  public void setSocket(Socket socket) {
    this.socket = socket;
  }
}
