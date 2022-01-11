import com.google.gson.JsonElement;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerMain {
    private static String configurationFile = "serverConfig.txt";
    private static final String usersFile = "users.json";
    private static final String postsFile = "posts.json";

    private static String serverIP = "localhost";
    private static int tcpPort = 2222;

    public static String multicastIP = "239.255.32.32";
    public static int multicastPort = 4444;

    private static int registryPort = 55555;
    private static String registerServiceName = "RMI-REGISTER";

    private static int bufferSize = 16 * 1024;

    private static int corePoolSize = 5;
    private static int maximumPoolSize = 15;
    private static int keepAliveTime = 30000;

    private static Map<String, User> users;
    private static Map<Integer, Post> posts;
    private static Map<String, Socket> loggedUsers;
    private static Set<Registrable> readyToBeRegistered;

    private static CallbackHandler callbackHandler;
    private static String callbackHandlerService = "RMI-FOLLOWER-CALLBACK";

    private static int calculationTime = 25000;
    private static double authorPercentage = 50;

    private static final AtomicBoolean stateChanged = new AtomicBoolean(false);
    private static int saveStateTime = 15 * 6000;

    private static int automaticLogoutCheckTime = 25000;

    private static int threadPoolTimeout = 10000;

    public static void main(String []args) {
        //Checks if is specified a different configuration file
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ServerMain [config file]");
            System.exit(1);
        }

        //Parses the configuration file
        parseConfigFile();

        //Initializes the state of the server
        users = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();
        loggedUsers = new ConcurrentHashMap<>();

        //Initializes the concurrent set to handle reads and writes from/for a client
        readyToBeRegistered = ConcurrentHashMap.newKeySet();

        //Recovers the state of the server
        RecoverState.readUsers(users, usersFile);
        RecoverState.readPosts(posts, postsFile);

        callbackHandler = new CallbackHandler();
        initializeRMIServices();

        //Opens the selector
        Selector selector = null;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            System.err.println("Error while opening selector: (" + e.getMessage() + ")");
            System.exit(1);
        }

        //Initializes the activeThread list
        List<Thread> activeThread = new ArrayList<>();

        //Creates and starts the thread that calculates the revenue
        Thread revenueThread = new Thread(new RevenueCalculator(users, posts, calculationTime, authorPercentage, multicastIP, multicastPort, stateChanged));
        revenueThread.start();
        activeThread.add(revenueThread);

        //Creates and starts the thread that periodically saves the state of the server
        Thread saveStateThread = new Thread(new SaveState(users, posts, usersFile, postsFile, stateChanged, saveStateTime));
        saveStateThread.start();
        activeThread.add(saveStateThread);

        //Creates and starts the thread that periodically logs out inactive users logged in
        Thread automaticLogoutThread = new Thread(new AutomaticLogoutHandler(loggedUsers, automaticLogoutCheckTime));
        automaticLogoutThread.start();
        activeThread.add(automaticLogoutThread);

        //Creates the ThreadPool to handle clients requests
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        //Creates the ShutdownHook to terminate the server correctly
        ShutdownHandler shutdownHandler = new ShutdownHandler(usersFile, postsFile, users, posts, threadPool, activeThread, stateChanged, selector, threadPoolTimeout);

        //Opens the server
        multiplexChannels(threadPool, selector);
    }

    /**
     * Manages the connections
     * @param threadPool thread pool that executes tasks
     * @param selector selector that multiplexes the channels
     */
    private static void multiplexChannels(Executor threadPool, Selector selector) {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
             ServerSocket serverSocket = serverSocketChannel.socket()) {

            serverSocket.bind(new InetSocketAddress(serverIP, tcpPort)); //Binds IP and port to the socket
            serverSocketChannel.configureBlocking(false); //Sets the non-blocking mode
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); //Register the channel on the selector

            System.out.println("Server started");

            JsonElement jsonElement = null;
            while (true) {
                //Waits for an operation
                selector.select();

                if (!selector.isOpen()) break; //Thread termination

                //Re-registers the channels ready to be served again
                for (Registrable r : readyToBeRegistered) {
                    try {
                        //Re-registers the channel
                        r.getClientChannel().register(selector, r.getOperation(), r.getByteBuffer());
                        //Gets the response to send
                        jsonElement = r.getJsonElement();
                    } catch (ClosedChannelException e) {
                        System.err.println("Error while registering a channel: " + e.getMessage());
                        break;
                    }
                    readyToBeRegistered.remove(r);
                }

                //Gets the ready key
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
                    //Gets the key
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    try {
                        if (key.isAcceptable()) { //New connection
                            ServerSocketChannel server = (ServerSocketChannel) key.channel(); //Gets the channel
                            SocketChannel client = server.accept(); //Accepts the connection
                            System.out.println(client);

                            client.configureBlocking(false); //Sets non-blocking mode

                            //Registers the channel in read mode
                            SelectionKey clientKey = client.register(
                                    selector,
                                    SelectionKey.OP_READ
                            );

                            //Allocates the buffer and attach it to the channel
                            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
                            clientKey.attach(byteBuffer);

                        } else if (key.isReadable()) { //Channel ready in read mode
                            key.cancel();
                            threadPool.execute(new ReaderWorker(key, users, posts, loggedUsers, callbackHandler, readyToBeRegistered, selector, stateChanged));
                        } else if (key.isWritable()) { //Channel ready in write mode
                            key.cancel();
                            threadPool.execute(new WriterWorker(key, readyToBeRegistered, selector, jsonElement));
                        }
                    } catch (IOException e) {
                        System.err.println("Error serving requests: " + e.getMessage());
                        key.cancel();
                        key.channel().close();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Server closed");
    }

    /**
     * Initializes the registration service
     */
    private static void initializeRMIServices() {
        SignUpService registerService = new SignUpService(users, stateChanged);
        try {
            //Exports the objects
            RegisterInterface registerStub = (RegisterInterface) UnicastRemoteObject.exportObject(registerService, 0);
            CallbackHandlerInterface callbackHandlerStub = (CallbackHandlerInterface) UnicastRemoteObject.exportObject(callbackHandler, 0);

            //Creates the registry
            LocateRegistry.createRegistry(registryPort);
            //Gets the registry
            Registry registry = LocateRegistry.getRegistry(registryPort);

            //Binds the stubs to the correspondents names
            registry.rebind(registerServiceName, registerStub);
            registry.rebind(callbackHandlerService, callbackHandlerStub);
        } catch (RemoteException e) {
            System.err.println("Error while exporting Register object: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Parses the configuration file
     */
    private static void parseConfigFile() {
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(configurationFile));
            String line;

            while ((line = fileReader.readLine()) != null) {
                switch (line.split("=")[0]) {
                    case "SERVER-IP" -> serverIP = line.split("=")[1];

                    case "TCP-PORT" -> {
                        tcpPort = Integer.parseInt(line.split("=")[1]);

                        if (tcpPort < 0 || tcpPort > 65535) {
                            System.err.println("TCP-PORT must be between 0 and 65535");
                            System.exit(1);
                        }
                    }

                    case "MULTICAST-IP" -> multicastIP = line.split("=")[1];

                    case "MCAST-PORT" -> {
                        multicastPort = Integer.parseInt(line.split("=")[1]);

                        if (multicastPort < 0 || multicastPort > 65535) {
                            System.err.println("MULTICAST-PORT must be between 0 and 65535");
                            System.exit(1);
                        }
                    }

                    case "REGISTRY-PORT" -> {
                        registryPort = Integer.parseInt(line.split("=")[1]);

                        if (registryPort < 0 || registryPort > 65535) {
                            System.err.println("REGISTRY-PORT must be between 0 and 65535");
                            System.exit(1);
                        }
                    }

                    case "BUFFER-SIZE" -> {
                        bufferSize = Integer.parseInt(line.split("=")[1]);

                        if (bufferSize < 16) {
                            System.err.println("BUFFER-SIZE should ba at least 16");
                            System.exit(1);
                        }
                    }

                    case "RMI-REGISTER-SERVICE" -> registerServiceName = line.split("=")[1];

                    case "POOL-SIZE" -> {
                        corePoolSize = Integer.parseInt(line.split("=")[1]);

                        if (corePoolSize < 0) {
                            System.err.println("POOL-SIZE cannot be negative");
                            System.exit(1);
                        }
                    }

                    case "MAX-POOL-SIZE" -> {
                        maximumPoolSize = Integer.parseInt(line.split("=")[1]);

                        if (maximumPoolSize < 0) {
                            System.err.println("MAX-POOL-SIZE cannot be negative");
                            System.exit(1);
                        }
                    }

                    case "KEEPALIVE" -> {
                        keepAliveTime = Integer.parseInt(line.split("=")[1]);

                        if (keepAliveTime < 0) {
                            System.err.println("KEEPALIVE cannot be negative");
                            System.exit(1);
                        }
                    }

                    case "THREADPOOL-TIMEOUT" -> {
                        threadPoolTimeout = Integer.parseInt(line.split("=")[1]);

                        if (threadPoolTimeout < 0) {
                            System.err.println("THREADPOOL-TIMEOUT cannot be negative");
                            System.exit(1);
                        }
                    }

                    case "RMI-FOLLOWER-SERVICE" -> callbackHandlerService = line.split("=")[1];

                    case "REVENUE-TIME" -> {
                        calculationTime = Integer.parseInt(line.split("=")[1]);

                        if (calculationTime < 0) {
                            System.err.println("REVENUT-TIME cannot be negative");
                            System.exit(1);
                        }
                    }

                    case "AUTHOR-PERCENTAGE" -> {
                        authorPercentage = Double.parseDouble(line.split("=")[1]);

                        if (authorPercentage < 0 | authorPercentage > 100) {
                            System.err.println("AUTHOR-PERCENTAGE must be between 0 and 100");
                            System.exit(1);
                        }
                    }

                    case "BACKUP-TIME" -> {
                        saveStateTime = Integer.parseInt(line.split("=")[1]);

                        if (saveStateTime < 0) {
                            System.err.println("BACKUP-TIME cannot be negative");
                            System.exit(1);
                        }
                    }

                    case "AUTOMATIC-LOGOUT" -> {
                        automaticLogoutCheckTime = Integer.parseInt(line.split("=")[1]);

                        if (automaticLogoutCheckTime < 0) {
                            System.err.println("AUTOMATIC-LOGOUT cannot be negative");
                            System.exit(1);
                        }
                    }

                    default -> {
                        if (!line.equals("") && !line.startsWith("#")) {
                            System.err.println("Invalid option: " + line);
                            System.exit(1);
                        }
                    }
                }
            }

            if (maximumPoolSize < corePoolSize) {
                System.err.println("MAX-POOL-SIZE must be grater or equal then POOL-SIZE");
                System.exit(1);
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
