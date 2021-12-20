import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private static Map<String, User> users;
    private static Map<Integer, Post> posts;

    public static void main(String []args) {
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ServerMain [config file]");
            System.exit(1);
        }

        parseConfigFile();

        users = new ConcurrentHashMap<>();
        posts = new ConcurrentHashMap<>();

        RecoverState.readUsers(users, usersFile);
        RecoverState.readPosts(posts, postsFile);

        initializeRegisterService();

        ShutdownHandler shutdownHandler = new ShutdownHandler(usersFile, postsFile, users, posts);

    }

    private static void initializeRegisterService() {
        SignUpService registerService = new SignUpService(users, posts);
        try {
            RegisterInterface registerStub = (RegisterInterface) UnicastRemoteObject.exportObject(registerService, 0);

            LocateRegistry.createRegistry(registryPort);
            Registry registry = LocateRegistry.getRegistry(registryPort);
            registry.rebind(registerServiceName, registerStub);
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
                }
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
