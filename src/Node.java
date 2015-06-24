import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.cli.AlreadySelectedException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Node {
  static HashSet<String> slavesAddresses = new HashSet<String>();
  //static HashMap<String, Integer> slavesAddresses = new HashMap<String, Integer>();
  static HashMap<String, TimeMessage> slavesResponses = new HashMap<String, TimeMessage>();
  
  static Scanner userInput;
  static boolean keepWorking = true;

  // Input date format
  static SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");

  // default commandline syntax
  static String cmdLineSyntax = "java Node -m | -s -sf <filename> [-b] [-pp <ip:port>] [-t <mm/dd/yyyy h:mm:ss.fff>] [-td <milliseconds>] [-d <milliseconds>] [-l <logfile>]";
  // Helper class to print default help about all parametrs in organized way
  static HelpFormatter helpFormatter = new HelpFormatter();
  // Parsed date
  static Date dateStr = null;
  // IP address of the server
  static String ip = "127.0.0.1";
  // Port number of the server
  static int port = 2333;
  // String with formatted time
  static String timeStr = null;
  // Server local time in milliseconds (timestamp)
  static long time = System.currentTimeMillis();
  // Difference between server local time and actual physical time in milliseconds
  static long timeDifference = randomWithRange(-10000, 10000);
  // threshold in milliseconds
  static int threshold = 1000;
  // slaves filename
  static String slavesFileName = null;
  // log filename
  static String logFileName = null;
  // to write log data
  static PrintWriter logWriter = null;
  static FileOutputStream log = null;
  
  // number of current synchronization round
  static int roundID = 0;  

  // difference that was not corrected yet
  static int timeCorrection;
  static int correctionRoundID;
  
  // to track local time requests and apply corrections that not to affect general time continuity
  static long lastTimeRequest = 0;
  static float correctionPercentage = 0.50f;
  
  // in milliseconds
  static int refreshInterval = 100; 
  static long lastRefreshTime = 0; 
  
  static int syncInterval = 5000;
  static long lastSyncTime = 0;
  
  static int syncDuration = 1000;
  static long currentTime = 0;
  
  synchronized static void setTimeCorrection(int correction, int roundID) {
    timeCorrection = correction;
    correctionRoundID = roundID;
    log("Made correction of " + correction + " milliseconds in round no. " + roundID + "\n");
    log(dateFormatter.format(localTimeMillis()) + " >> ");
  }
  
  synchronized static void addTimeCorrection(int correction, int roundID) {
    timeCorrection += correction;
    correctionRoundID = roundID;
    log("Made a coorection of " + correction + " milliseconds in round no. " + roundID + "\n");
    log(dateFormatter.format(localTimeMillis()) + " >> ");
  }
  
  synchronized static void clearTimeCorrection(int correction) {
    timeCorrection = 0;
  }
  
  synchronized static int getCorrectionRoundID() {
    return correctionRoundID;
  }
  
  static int randomWithRange(int min, int max)
  {
     int range = (max - min) + 1;     
     return (int)(Math.random() * range) + min;
  }
  
  synchronized static long localTimeMillis() {
    return localTimeMillis(false);
  }
  
  synchronized static long localTimeMillis(boolean withCorrection) {
    if (lastTimeRequest == 0) {
      // if we are making local time request for the first time 
      lastTimeRequest = System.currentTimeMillis() + timeDifference;
      return lastTimeRequest + (withCorrection ? timeCorrection : 0);
    } else {
      // check how many milliseconds passed from previous time requests
      // and make corrections that is less than correctionPercentage
      long timePassed = System.currentTimeMillis() + timeDifference - lastTimeRequest;
      long allowedCorrection = (long) (Math.max(0, timePassed * correctionPercentage + timeCorrection) - timePassed * correctionPercentage);
      timeDifference += allowedCorrection;
      timeCorrection -= allowedCorrection;
      lastTimeRequest = System.currentTimeMillis() + timeDifference;
    }
    return System.currentTimeMillis() + timeDifference + (withCorrection ? timeCorrection : 0);
  }
  
  static void log(String message) {
    if (logWriter != null) {
      logWriter.write(message);
      logWriter.flush();
    }
    System.out.print(message);
  }
  
  public static void main(String[] args) throws ParseException, InterruptedException {
    // create Options object
    Options options = new Options();

    Option masterOpt = Option.builder("m")
            .desc("use node as a master")
            .longOpt("master")
            .build();
    
    Option slaveOpt = Option.builder("s")
            .desc("use node as a slave (default)")
            .longOpt("slave")
            .build();
    
    Option backgroundOpt = Option.builder("b")
            .desc("run node in background mode (daemon without user input)")
            .longOpt("background")
            .build();
    
    Option debugOpt = Option.builder("debug")
            .desc("run node in debug mode")
            .longOpt("debug")
            .build();
    
    // add options
    OptionGroup modeOptGroup = new OptionGroup();
    modeOptGroup.addOption(masterOpt);
    modeOptGroup.addOption(slaveOpt);
    modeOptGroup.setRequired(true);
    options.addOptionGroup(modeOptGroup);
    options.addOption(backgroundOpt);
    options.addOption(debugOpt);
    
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = null;
    
    try {
      cmd = parser.parse(options, args, true);
      
      if(cmd.hasOption("m")) {
        Option ipPortOpt = Option.builder("pp")
                .desc("address that the master should use to communicate with all of the slaves (default is 127.0.0.1:2333)")
                .longOpt("ip:port")
                .hasArgs()
                .valueSeparator(':')
                .argName("ip:port")
                .build();
        
        Option timeOpt = Option.builder("t")
                .desc("the process-local time that the master should be initialized with (default is current time)")
                .longOpt("time")
                .hasArgs()
                .argName("mm/dd/yyyy h:mm:ss.fff")
                .build();
        
        Option timeDifferenceOpt = Option.builder("td")
                .desc("the difference between process-local time that the master should be initialized with and actual physical time (default is random)")
                .longOpt("time-difference")
                .hasArgs()
                .argName("milliseconds")
                .build();
        
        Option thresholdOpt = Option.builder("d")
                .desc("threshold used in the fault tolerant average (default is 1000ms)")
                .longOpt("threshold")
                .hasArgs()
                .argName("milliseconds")
                .build();
        
        Option slavesFileOpt = Option.builder("sf")
                .desc("a file with as many lines as there are slaves, each line contains an \"ip:port\" address of a slave")
                .longOpt("slavesfile")
                .hasArgs()
                .argName("filename")
                .build();
        
        slavesFileOpt.setRequired(true);
        
        Option logFileOpt = Option.builder("l")
                .desc("filename to which log messages will be written")
                .longOpt("logfile")
                .hasArgs()
                .argName("filename")
                .build();
        
        options.addOption(ipPortOpt);
        options.addOption(timeOpt);
        options.addOption(timeDifferenceOpt);
        options.addOption(thresholdOpt);
        options.addOption(slavesFileOpt);
        options.addOption(logFileOpt);
        
        cmd = parser.parse(options, args, true);
        
        if(cmd.hasOption("ip:port")) {
          ip = cmd.getOptionValues("ip:port")[0];  // will be "ip"
          port = Integer.parseInt(cmd.getOptionValues("ip:port")[1]); // will be "port"
        } 
        
        if(cmd.hasOption("time")) {
          timeStr = cmd.getOptionValue("time");
          dateStr = dateFormatter.parse(timeStr);
          time = dateStr.getTime();
          timeDifference = time - System.currentTimeMillis();
        }
        
        if(cmd.hasOption("time-difference")) {
          timeDifference = Integer.parseInt(cmd.getOptionValue("time-difference"));
        }
        
        if(cmd.hasOption("threshold")) {
          threshold = Integer.parseInt(cmd.getOptionValue("threshold"));
        }
        
        if(cmd.hasOption("slavesfile")) {
          slavesFileName = cmd.getOptionValue("slavesfile");
          // check if file exists
          File f = new File(slavesFileName);
          if(!f.exists() || f.isDirectory()) { 
            throw new IncorrectSlavesFileException();
          } else {
            // read list of slaves' addresses to connect to
            log("Slaves' addresses to connect to are:\n");
            
            List<String> lines = Files.readAllLines(Paths.get(slavesFileName), Charset.defaultCharset());
            for (String line : lines) {
              log(line + "\n");
              slavesAddresses.add(line.trim());
            }
            if (slavesAddresses.isEmpty()) {
              //log("  <EMPTY>\n");
              throw new EmptySlavesFileException();
            }
          }
        }
        
        if(cmd.hasOption("logfile")) {
          logFileName = cmd.getOptionValue("logfile");
          // check if file exists
          /*
          File f = new File(logFileName);
          if(!f.exists() || f.isDirectory()) { 
            throw new IncorrectLogFileException();
          } else {
            logWriter = new PrintWriter(logFileName, "cp1251");
          }
          */
          //log = new FileOutputStream(logFileName);
          
          try {
            FileWriter fw = new FileWriter(logFileName, true);
            BufferedWriter bw = new BufferedWriter(fw);
            logWriter = new PrintWriter(bw);
          } catch (IOException e) {
            throw new IncorrectLogFileException();
          }            
        }
        
        // run in master mode (server) and show status data
        log("Node will perform master role (server)\n");
        log("IP = " + ip + ", port = " + port + "\n");
        log("Local time difference is: " + timeDifference + " milliseconds\n");
        String formattedDate = dateFormatter.format(localTimeMillis());
        log("Node's local time is: " + formattedDate + ", timestamp is: " + time + "\n");
        log("Threshold: " + threshold + " milliseconds\n");
        log("Logfile is: " + logFileName + "\n");
        log("Local address is: " + InetAddress.getLocalHost().getHostAddress() + "\n");
        
        Server server;
        
        // if we don't have a list of slave nodes therefore one multicast IP is used
        if (slavesAddresses == null) {
          server = new Server(ip, port);
        } else {
          // otherwise we specify just port and will use list of slave nodes
          server = new Server(port);
        }        
        log("Server UUID is: " + server.serverUUID + "\n");
        
        /*// uncomment this if you want to check how does filtering function work
        String[] uuids = {"0", "1", "2", "3", "4"};
        int[] deltas = {0, 10, 20, 30, 40};
        Integer threshold = 9;
        
        server.computeNonfaultyAverage(deltas, threshold);
        
        if (cmd.hasOption("debug")) {
          return;
        }
        */
        
        server.connect();
        
        // run thread that processes connections from the clients
        server.start();
        
        if (cmd.hasOption("background")) {
          while (true) {
            currentTime = System.currentTimeMillis();
            // need to refresh time display  
            if (lastRefreshTime + refreshInterval < currentTime) {
              log(dateFormatter.format(localTimeMillis()) + " >> \n");
              lastRefreshTime = currentTime;
            }
            
            // need to send synchronization request
            if (lastSyncTime + syncInterval < currentTime) {
              server.startSyncRound();
              lastSyncTime = currentTime;
            }
            
            Thread.sleep(Math.min(refreshInterval, syncInterval));
          }
        } else {
          userInput  = new Scanner(System.in);
          
          // the message that server wants to send to other clients
          String command;
          
          log("Type your command and press <Enter>\n");
          log("Possible commands are: \"sync\" to start sync round, and \"exit\" for exit\n\n");
          
          while(keepWorking){
            log(dateFormatter.format(localTimeMillis()) + " >> ");
            if (userInput.hasNextLine()) {
              command = userInput.nextLine();
            } else {
              command = "exit";
            }
            
            if (command.equals("exit")) {
              // notify to stop all threads and close all connections
              server.signalStop();
              keepWorking = false;
            } else if (command.equals("sync")) {
                // notify to stop all threads and close all connections
                server.startSyncRound();
                /*
                while (Server.keepSynchronizing) {
                  log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> \n");
                  Thread.sleep(10);
                }
                */
            } else if (command.equals("")) {
              // just do nothing
            } else {
              // print warning
              log("You can use only commands \"sync\" or \"exit\"\n");
            }
          }
        }
        
        // wait before server thread will stop
        server.join();
        
        log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
        log("Server was shutted down\n");
        
      } else if(cmd.hasOption("s")) {
        Option ipPortOpt = Option.builder("pp")
                .desc("address that the slave should listen on for server's messages (default is 127.0.0.1:2333)")
                .longOpt("ip:port")
                .hasArgs()
                .valueSeparator(':')
                .argName("ip:port")
                .build();
        
        Option timeOpt = Option.builder("t")
                .desc("the process-local time that the master should be initialized with (default is current time)")
                .longOpt("time")
                .hasArgs()
                .argName("mm/dd/yyyy h:mm:ss.ffff")
                .build();
        
        Option timeDifferenceOpt = Option.builder("td")
                .desc("the difference between process-local time that the master should be initialized with and actual physical time (default is random)")
                .longOpt("time-difference")
                .hasArgs()
                .argName("milliseconds")
                .build();
        
        Option logfileOpt = Option.builder("l")
                .desc("filename to which log messages will be written")
                .longOpt("logfile")
                .hasArgs()
                .argName("filename")
                .build();
        
        options.addOption(ipPortOpt);
        options.addOption(timeOpt);
        options.addOption(timeDifferenceOpt);
        options.addOption(logfileOpt);
        
        cmd = parser.parse(options, args, true);
        
        if(cmd.hasOption("ip:port")) {
          ip = cmd.getOptionValues("ip:port")[0];  // will be "ip"
          port = Integer.parseInt(cmd.getOptionValues("ip:port")[1]); // will be "port"
        }
        
        if(cmd.hasOption("time")) {
          timeStr = cmd.getOptionValue("time");
          dateStr = dateFormatter.parse(timeStr);
          time = dateStr.getTime();
          timeDifference = time - System.currentTimeMillis();
        }
        
        if(cmd.hasOption("time-difference")) {
          timeDifference = Integer.parseInt(cmd.getOptionValue("time-difference"));
        }
         
        if(cmd.hasOption("logfile")) {
          logFileName = cmd.getOptionValue("logfile");
          // check if file exists
          /*
          File f = new File(logFileName);
          if(!f.exists() || f.isDirectory()) { 
            throw new IncorrectLogFileException();
          } else {
            logWriter = new PrintWriter(logFileName, "cp1251");
          }
          */
          //log = new FileOutputStream(logFileName);
          
          try {
            FileWriter fw = new FileWriter(logFileName, true);
            BufferedWriter bw = new BufferedWriter(fw);
            logWriter = new PrintWriter(bw);
          } catch (IOException e) {
            throw new IncorrectLogFileException();
          }            
        }
        
        // run in master mode (server) and show status data
        log("Node will perform slave role (client)\n");
        log("IP = " + ip + ", port = " + port + "\n");
        log("Local time difference is: " + timeDifference + " milliseconds\n");
        String formattedDate = dateFormatter.format(localTimeMillis());
        log("Node's local time is: " + formattedDate + ", timestamp is: " + time + "\n");
        log("Logfile is: " + logFileName + "\n");
        log("Local address is: " + InetAddress.getLocalHost().getHostAddress() + "\n");
        
        Client client;
        client = new Client(ip, port);
        log("Client UUID is: " + client.clientUUID + "\n");
        
        client.connect();
        
        // run thread that processes connections from the clients
        client.start();
        
        if (cmd.hasOption("background")) {
          while (true) {
            currentTime = System.currentTimeMillis();
            // need to refresh time display  
            if (lastRefreshTime + refreshInterval < currentTime) {
              log(dateFormatter.format(localTimeMillis()) + " >> \n");
              lastRefreshTime = currentTime;
            }
            
            Thread.sleep(refreshInterval);
          }
        } else {
          userInput  = new Scanner(System.in);
          
          // the message that server wants to send to other clients
          String command;
          
          log("Type your command and press <Enter>\n");
          log("Only possible command is: \"exit\" for exit\n\n");
          
          while(keepWorking){
            log(dateFormatter.format(localTimeMillis()) + " >> ");
            if (userInput.hasNextLine()) {
              command = userInput.nextLine();
            } else {
              command = "exit";
            }
            
            if (command.equals("exit")) {
              // notify to stop all threads and close all connections
              client.signalStop();
              keepWorking = false;
            } else if (command.equals("")) {
              // just do nothing
            } else {
              // print warning
              log("You can use only command \"exit\"\n");
            }
          }
        }
        
        // wait before client thread will stop
        client.join();
        
        log(Node.dateFormatter.format(Node.localTimeMillis()) + " >> ");
        log("Client was shutted down\n");
      }
      
      if (logWriter != null) {
        logWriter.close();
      }
      
      if (log != null) {
        log.close();
      }
    } 
    catch (java.text.ParseException e) {
      log("Incorrect time format. Use mm/dd/yyyy h:mm:ss.fff instead, where mm - month, dd - day, yyyy - year, h - hour, mm - minutes, ss - seconds, fff - milliseconds\n");
    } catch (AlreadySelectedException e) {
      log("Multiple modes of operation selected. You can't use both: -m (master) and -s (slave) modes. You have to select between two of them.\n");
    } catch (IncorrectSlavesFileException e) {
      log("File \"" + slavesFileName + "\" doesn't exist or inaccessible.\n");
    } catch (IncorrectLogFileException e) {
      log("File \"" + logFileName + "\" doesn't exist or inaccessible.\n");
    } catch(SocketException e) {
      log("Could not use port: " + port + ". It's already busy or inaccessible.\n");
    } catch (IOException e) {
      log("Error reading data from file. File may be inaccessible.\n");
    } catch (MissingOptionException e) {
      log(e.getMessage());
      // automatically generate the help statement
      helpFormatter.printHelp(cmdLineSyntax, options);
    } catch (EmptySlavesFileException e) {
      log("File \"" + slavesFileName + "\" is empty. Add IPs and ports of the slaves to it.\n");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
