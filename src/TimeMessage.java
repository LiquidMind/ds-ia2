import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class TimeMessage implements Serializable {
  /**
   * 
   */
  private static final long serialVersionUID = 7887390717087302051L;
  
  // possible IDs of actions 
  public static final int ACTION_REQUEST = 0;
  public static final int ACTION_CORRECT = 1;  
  
  // Current action ID
  int actionID;
  // Id of synchronization round
  int roundID;
  //IP address of the master node to which response should be sent
  String masterIP;
  // Port of the master node to which response should be sent
  int masterPort;
  // IP address of the slave node to which packet was sent
  String slaveIP;
  // Port of the slave node to which packet was sent
  int slavePort;
  // UUID of the slave node to which packet was sent
  String slaveUUID;
  
  // Initial timestamp added by the master when packet was created
  long tsA1;
  // Timestamp added by the slave when he received packet
  long tsB2;
  // Last timestamp added by the master after he received response
  long tsA3;
  // Correction to apply in milliseconds if action is ACTION_CORRECT
  int correction;
  
  public TimeMessage() {
  }
  
  static void log(String messsage) {
    Node.log(messsage);
  }
  
  public void setActionID(int actionID) {
    this.actionID = actionID;
  }
  
  public void setRoundID(int roundID) {
    this.roundID = roundID;
  }
  
  public void setMasterIP(String ip) {
    masterIP = ip;
  }
  
  public void setMasterPort(int port) {
    masterPort = port;
  }
  
  public void setSlaveIP(String ip) {
    slaveIP = ip;
  }
  
  public void setTsA1(long ts) {
    tsA1 = ts;
  }
  
  public void setTsB2(long ts) {
    tsB2 = ts;
  }
  
  public void setTsA3(long ts) {
    tsA3 = ts;
  }
  
  public void setCorrection(int correction) {
    this.correction = correction;
  }
  
  static byte[] serialize(TimeMessage msg) {
    return serializeObject(msg);
  }
  
  static TimeMessage deserialize(byte[] bytes) {
    return (TimeMessage)deserializeObject(bytes);
  }
  
  static byte[] serializeObject(Object obj) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutput out = null;
    try {
      out = new ObjectOutputStream(bos);   
      out.writeObject(obj);
      out.flush();
      byte[] bytes = bos.toByteArray(); 
      //log("Size of serialized object is: " + bytes.length + " bytes\n");
      //log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
      return bytes;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      //System.out.println("Finally");
      try {
        if (out != null) {
          out.close();
        }
      } catch (IOException e) {
        new RuntimeException(e);
      }
      try {
        bos.close();
      } catch (IOException e) {
        new RuntimeException(e);
      }
    }
  }
  
  static Object deserializeObject(byte[] bytes) {
    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    ObjectInput in = null;
    try {
      in = new ObjectInputStream(bis);
      Object obj = in.readObject();
      return obj;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        bis.close();
      } catch (IOException e) {
        new RuntimeException(e);
      }
      try {
        if (in != null) {
          in.close();
        }
      } catch (IOException e) {
        new RuntimeException(e);
      }
    }
  }  
}
