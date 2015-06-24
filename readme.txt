Name of the image is: liquidmind/openjdk-7-jdk_git_screen_ia2

Sources are in the folder /home/github/ds-ia2/src
Executable classes files are in folder /home/github/ds-ia2/bin

If you want to recompile files you can use command:
javac -cp ".:/home/github/ds-ia2/lib/commons-cli-1.3.jar" -d /home/github/ds-ia2/bin /home/github/ds-ia2/src/*.java

Before running applications you have to change working forlder to folder with class files using command:
cd /home/github/ds-ia2

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

1. !!! You need to find IP address of your docker virtual machine and write it in file "slaves.txt" instead of 192.168.56.102 !!!

2. Easiest way that allows to run applications on completely separated separated containers is to run multiple containers from the same image. You need to open as many instances of boot2docker and execute "docker run" command like this in each of them:
docker run -i -t -p 23000:23000 liquidmind/openjdk-7-jdk_git_screen_ia2

3. After that you can run Client in each container using command:
java -cp "lib/commons-cli-1.3.jar:bin" Node -s -b --ip:port "127.0.0.1:23000" -l "logs/log_slave_23000.txt"
Where 23000 is port for this client.

4. By default new client after start will get random time difference within -10 and +10 seconds (-10.000 and +10.000 milliseconds) from system time. If you want to specify time difference manually use parameter "-td <milliseconds>". For example command
java -cp "lib/commons-cli-1.3.jar:bin" Node -s -b --ip:port "127.0.0.1:23000" -l "logs/log_slave_23000.txt" -td "1000"
will run slave with time 1 second (1000 milliseconds) ahead of system time.

5. To run slave directly from docker console you can use following command:
docker run -i -t -p 23000:23000/udp liquidmind/openjdk-7-jdk_git_screen_ia2 java -cp "./home/github/ds-ia2/bin/commons-cli-1.3.jar::./home/github/ds-ia2/bin" Node -s -b --ip:port "127.0.0.1:23000" -l "./home/github/ds-ia2/log/log_slave_23000.txt" -td "1000"

And you can run server node with the command:
docker run -i -t -p 2333:2333/udp liquidmind/openjdk-7-jdk_git_screen_ia2 java -cp "./home/github/ds-ia2/lib/commons-cli-1.3.jar::./home/github/ds-ia2/bin" Node -m -b -l "./home/github/ds-ia2/log/log_master.txt" -td "1000" -slavesfile  "./home/github/ds-ia2/src/slaves.txt" -pp 127.0.0.1:2333

6. File with list of the slaves servers is located at "/home/github/ds-ia2/src/slaves.txt" and contains 10 slave nodes on IP 192.168.56.102 and ports from 23000 to 23009.

7. If you want to watch logs you can tonnel it to awk or some other tool.
