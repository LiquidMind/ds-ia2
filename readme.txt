Name of the image is: liquidmind/openjdk-7-jdk_git_screen_ia2

Sources are in the folder /home/github/ds-ia2/src
Executable classes files are in folder /home/github/ds-ia2/bin

If you want to recompile files you can use command:
javac -cp ".:/home/github/ds-ia2/lib/commons-cli-1.3.jar" -d /home/github/ds-ia2/bin /home/github/ds-ia2/src/*.java

Before running applications you have to change working forlder to folder with class files using command:
cd /home/github/ds-ia2

To run application type command:
java -cp ".:lib/commons-cli-1.3.jar:bin" Node -m | -s -sf <filename> [-b] [-d] [-pp <ip:port>] [-t <mm/dd/yyyy h:mm:ss.fff>] [-td <milliseconds>] [-th <milliseconds>] [-l <logfile>]

usage: java Node -m | -s -sf <filename> [-b] [-pp <ip:port>] [-t
            <mm/dd/yyyy h:mm:ss.fff>] [-td <milliseconds>] [-d
            <milliseconds>] [-l <logfile>]
 -b,--background                        run node in background mode
                                        (daemon without user input)
 -d,--debug                             run node in debug mode
 -l,--logfile <filename>                filename to which log messages
                                        will be written
 -m,--master                            use node as a master
 -pp,--ip:port <ip:port>                address that the master should use
                                        to communicate with all of the
                                        slaves (default is 127.0.0.1:2333)
 -s,--slave                             use node as a slave (default)
 -sf,--slavesfile <filename>            a file with as many lines as there
                                        are slaves, each line contains an
                                        "ip:port" address of a slave
 -t,--time <mm/dd/yyyy h:mm:ss.fff>     the process-local time that the
                                        master should be initialized with
                                        (default is current time)
 -td,--time-difference <milliseconds>   the difference between
                                        process-local time that the master
                                        should be initialized with and
                                        actual physical time (default is
                                        random)
 -th,--threshold <milliseconds>         threshold used in the fault
                                        tolerant average (default is
                                        1000ms)

Easiest way that allows to run applications on completely separated separated containers is to run multiple containers from the same image. You need to open as many instances of boot2docker and execute "docker run" command like this in each of them:
docker run -i -t -p 23000:23000 liquidmind/openjdk-7-jdk_git_screen_ia2

After that you can run Client in each container using command:
java -cp ".:lib/commons-cli-1.3.jar:bin" Node -s 127.0.0.1:23000 -l "log/log_slave_23000.txt"

. Take into consideration that in this case each instance will have its own IP address and you need to look at IP address of each machine (Server shows its IP after start). And use IP address of server machine while running clients.

To test applications you should:
1. If you want to recompile files you can use command:
javac -d /home/github/ds-ia1/bin /home/github/ds-ia1/src/*.java
2. Before running applications you have to change working forlder to folder with class files using command:
cd /home/github/ds-ia1/bin
3. Run "java Server [<SERVER_PORT>]" command. It will run server application on default 2333 port (or <SERVER_PORT> if specified) that will show IP to connect to. If server and client are running on the same machine/container "localhost" may be used instead of the server IP.
4. Run "java Client <SERVER_IP> [<SERVER_PORT>]" command where <SERVER_IP> and <SERVER_PORT> is IP and port of the server from the previous step (or "localhost" without quotes in case of Server and Client located on the same computer)
5. You may run as many clients as you want executing command from step 2 multiple times.
6. You may enter message in any client or at the server. Message will be echoed to each client except those who sent message.
7. To exit server or clients enter "exit" or simply close an application.

P.S. In case if you want to run already compiled applications you should use =java version "1.7.0_79".