import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class ServerMain {
    private static String configurationFile = "serverConfig.txt";
    private static final String usersFile = "users.json";
    private static final String postsFile = "posts.json";

    private static String serverIP = "localhost";
    private static int tcpPort = 2222;
    private static int udpPort = 3333;
    private static String multicastIP = "239.255.32.32";
    private static int multicastPort = 4444;
    private static String registryHost = "localhost";
    private static int registryPort = 55555;
    private static String registerServiceName = "RMI-REGISTER";
    private static int socketTimeout = 60000;
    private static int bufferSize = 16 * 1024;

    private static int corePoolSize = 5;
    private static int maximumPoolSize = 15;
    private static int keepAliveTime = 30000;

    private static Map<String, User> users;
    private static Map<Integer, Post> posts;
    private static Map<String, Socket> loggedUsers;
    private static Set<Registable> readyToBeRegistered;

    private static CallbackHandler callbackHandler;
    private static String callbackHandlerService = "RMI-FOLLOWER-CALLBACK";

    public static void main(String []args) {
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ServerMain [config file]");
            System.exit(1);
        }

        parseConfigFile();

        users = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();
        loggedUsers = new ConcurrentHashMap<>();

        readyToBeRegistered = ConcurrentHashMap.newKeySet();

        RecoverState.readUsers(users, usersFile);
        RecoverState.readPosts(posts, postsFile);

        callbackHandler = new CallbackHandler();
        initializeRegisterService();

        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        ShutdownHandler shutdownHandler = new ShutdownHandler(usersFile, postsFile, users, posts, threadPool);
        multiplexChannels(threadPool);
    }

    private static void multiplexChannels(Executor threadPool) {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
             ServerSocket serverSocket = serverSocketChannel.socket();
             Selector selector = Selector.open()) {

            serverSocket.bind(new InetSocketAddress(serverIP, tcpPort)); //Associa il socket alla porta
            serverSocketChannel.configureBlocking(false); //Imposta la modalità non bloccante
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); //Registra il canale sul Selector

            System.out.println("Server started");

            while (true) {
                //Attesa di una richiesta
                selector.select();

                for (Registable r : readyToBeRegistered) {
                    try {
                        r.getClientChannel().register(selector, r.getOperation(), r.getByteBuffer());
                    } catch (ClosedChannelException e) {
                        System.err.println("Error while registering a channel: " + e.getMessage());
                        break;
                    }
                    readyToBeRegistered.remove(r);
                }

                //Recupera le chiavi pronte
                Set<SelectionKey> readyKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = readyKeys.iterator();

                while (iterator.hasNext()) {
                    //Recupera la chiave
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    try {
                        if (key.isAcceptable()) { //Nuova connessione
                            ServerSocketChannel server = (ServerSocketChannel) key.channel(); //Recupera il channel
                            SocketChannel client = server.accept(); //Accetta la connessione del client
                            System.out.println(client);

                            client.configureBlocking(false); //Imposta il canale in modalità non bloccante
                            SelectionKey clientKey = client.register( //Registra il canale sul Selector in lettura
                                    selector,
                                    SelectionKey.OP_READ
                            );

                            //Alloca il buffer e fa l'attach con il canale
                            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
                            clientKey.attach(byteBuffer);

                        } else if (key.isReadable()) { //Il channel è pronto in lettura
                            key.cancel();
                            threadPool.execute(new ReaderWorker(key, users, posts, loggedUsers, callbackHandler, readyToBeRegistered, selector));
                        } else if (key.isWritable()) { //Il client è pronto in scrittura
                            key.cancel();
                            threadPool.execute(new WriterWorker(key, readyToBeRegistered, selector));
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
    }

    private static void initializeRegisterService() {
        SignUpService registerService = new SignUpService(users, posts);
        try {
            RegisterInterface registerStub = (RegisterInterface) UnicastRemoteObject.exportObject(registerService, 0);
            CallbackHandlerInterface callbackHandlerStub = (CallbackHandlerInterface) UnicastRemoteObject.exportObject(callbackHandler, 0);

            LocateRegistry.createRegistry(registryPort);
            Registry registry = LocateRegistry.getRegistry(registryPort);

            registry.rebind(registerServiceName, registerStub);
            registry.rebind(callbackHandlerService, callbackHandlerStub);
        } catch (RemoteException e) {
            System.err.println("Error while exporting Register object: " + e.getMessage());
            System.exit(1);
        }
    }

    //Parsing del file di configurazione del client
    private static void parseConfigFile() {
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(configurationFile));
            String line;

            while ((line = fileReader.readLine()) != null) {
                switch (line.split("=")[0]) {
                    case "SERVER-IP" -> serverIP = line.split("=")[1];

                    case "TCP-PORT" -> tcpPort = Integer.parseInt(line.split("=")[1]);

                    case "UDP-PORT" -> udpPort = Integer.parseInt(line.split("=")[1]);

                    case "MULTICAST-IP" -> multicastIP = line.split("=")[1];

                    case "MCAST-PORT" -> multicastPort = Integer.parseInt(line.split("=")[1]);

                    case "REGISTRY-HOST" -> registryHost = line.split("=")[1];

                    case "REGISTRY-PORT" -> registryPort = Integer.parseInt(line.split("=")[1]);

                    case "SOCKET-TIMEOUT" -> socketTimeout = Integer.parseInt(line.split("=")[1]);

                    case "RMI-SERVICE" -> registerServiceName = line.split("=")[1];

                    case "POOL-SIZE" -> corePoolSize = Integer.parseInt(line.split("=")[1]);

                    case "MAX-POOL-SIZE" -> maximumPoolSize = Integer.parseInt(line.split("=")[1]);

                    case "KEEPALIVE" -> keepAliveTime = Integer.parseInt(line.split("=")[1]);
                }
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
