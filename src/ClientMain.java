import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientMain {
    private static String configurationFile = "clientConfig.txt";

    private static int registryPort = 11111; //TODO Mettere una porta di default più significativa
    private static String registryHost = "localhost";
    private static String registerServiceName = "RMI-REGISTER";
    private static String serverIP = "localhost";
    private static int tcpPort = 2222;
    private static int bufferSize = 16 * 1024;
    private static SocketChannel socketChannel = null;
    private static ByteBuffer buffer;

    public static void main(String []args) {
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ClientMain [config file]");
            System.exit(1);
        }

        parseConfigFile();
        buffer = ByteBuffer.allocate(bufferSize);
        RegisterInterface register = getRemoteRegisterObject();

        Scanner cliScanner = new Scanner(System.in);
        String command;
        boolean termination = false;

        while (!termination) {
            System.out.print("> ");
            command = cliScanner.nextLine();

            String []arguments = command.split(" ");
            switch (arguments[0]) {
                case "register" : {
                    if (socketChannel != null) {
                        System.err.println("There's a user already logged");
                        break;
                    }
                    registerUser(register, arguments);
                    break;
                }
                case "login" : {
                    socketChannel = loginUser(command);
                    break;
                }
                case "logout" : break;
                case "list" : {
                    if (arguments.length != 2 || (!arguments[1].equals("users") && !arguments[1].equals("following") && !arguments[1].equals("followers"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'list users' or 'list following'");
                        break;
                    }

                    if (arguments[1].equals("users")) { //list users

                    } else if (arguments[1].equals("followers")) { //list followers

                    } //list following
                    break;
                }
                case "follow" : break;
                case "unfollow" : break;
                case "blog" : break;
                case "post" : break;
                case "show" : {
                    if (arguments.length != 2 || (!arguments[1].equals("feed") && !arguments[1].equals("post"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'show feed' or 'show post'");
                        break;
                    }

                    if (arguments[1].equals("feed")) { //show feed

                    } else { //show post

                    }
                    break;
                }
                case "delete" : break;
                case "rewin" : break;
                case "rate" : break;
                case "comment" : break;
                case "wallet" : {
                    if (arguments.length > 2 || (arguments.length == 2 && !arguments[1].equals("btc"))) {
                        System.err.println("Invallid command '" + command + "'. Use 'wallet' or 'wallet btc'");
                        break;
                    }

                    if (arguments.length == 2) { //wallet btc

                    } else { //wallet

                    }
                    break;
                }
                case "exit" : { //TODO Scrivere nella relazione che si è voluto inserire questo comando per terminare il client
                    termination = true;
                    break;
                }
                default: System.err.println("Unknown command: " + command);
            }
        }
    }

    private static void sendRequest(String command) throws IOException{
        if (socketChannel == null) {
            System.err.println("No user is logged in");
            return;
        }

        buffer.clear();

        //Puts the command length
        buffer.putInt(command.length());
        //Puts the command
        buffer.put(command.getBytes());

        //Sets the buffer ready for writing to the channel
        buffer.flip();
        //Writes to the channel
        socketChannel.write(buffer);
    }

    private static SocketChannel loginUser(String command) {
        if (command.split(" ").length != 3) {
            System.err.println("Usage: login username password");
            return null;
        }

        //Checks if a user is already logged in
        if (socketChannel != null) {
            System.err.println("There's a user already logged");
            return socketChannel;
        }

        try {
            //Connects to the server
            socketChannel = SocketChannel.open(new InetSocketAddress(serverIP, tcpPort));

            //Sends request to the server
            sendRequest(command);

            buffer.clear();
            //Reads the response
            socketChannel.read(buffer);
            //Sets the buffer ready to be read
            buffer.flip();
            int responseId = buffer.getInt();

            if (responseId == 0)
                System.out.println("> Logged correctly");
            else
                System.err.println("Username or password not correct");

            //Returns the socketChannel
            return socketChannel;
        } catch (IOException e) {
            System.err.println("Error during login, please try again (" + e.getMessage() + ")");
            return null;
        }
    }

    //Registers a new user to WINSOME
    private static void registerUser(RegisterInterface register, String[] arguments) {
        if (arguments.length > 8) {
            System.err.println("Usage: register username password tags (max 5 tag)");
            return;
        }

        List<String> tag = new ArrayList<>(Arrays.asList(arguments).subList(3, arguments.length));

        try {
            int result;
            if ((result = register.register(arguments[1], arguments[2], tag)) != 0) {
                if (result == -1) System.err.println("Error server side");
                if (result == 1) System.err.println("Password field is empty");
                if (result == 2 || result == 3) System.err.println("Registration requires minimum 1 tag and maximum 5");
                if (result == 4) System.err.println("User '" + arguments[1] + "' already registered");
            }
        } catch (RemoteException e) {
            System.err.println("Error while registering new user: " + e.getMessage());
        }
    }

    //Retrieving the remote object for the registration process
    private static RegisterInterface getRemoteRegisterObject() {
        try {
            Registry registry = LocateRegistry.getRegistry(registryPort);
            return (RegisterInterface) registry.lookup(registerServiceName);
        } catch (RemoteException e) {
            System.err.println("Error with the remote object: " + e.getMessage());
            System.exit(1);
        } catch (NotBoundException e) {
            System.err.println("Error while retrieving the remote object: " + e.getMessage());
            System.exit(1);
        }

        return null;
    }

    //Parsing del file di configurazione del client
    private static void parseConfigFile() {
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(configurationFile));
            String line;

            while ((line = fileReader.readLine()) != null) {
                switch (line.split("=")[0]) {
                    case "REGISTRY-PORT" -> registryPort = Integer.parseInt(line.split("=")[1]);

                    case "REGISTRY-HOST" -> registryHost = line.split("=")[1];

                    case "RMI-SERVICE" -> registerServiceName = line.split("=")[1];

                    case "SERVER-IP" -> serverIP = line.split("=")[1];

                    case "TCP-PORT" -> tcpPort = Integer.parseInt(line.split("=")[1]);
                    //TODO Aggiungere ulteriori case
                }
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
