import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientMain {
    private static String configurationFile = "clientConfig.txt";

    private static int registryPort = 11111; //TODO Mettere una porta di default più significativa
    private static String registryHost = "localhost";
    private static String registerServiceName = "RMI-REGISTER";

    public static void main(String []args) {
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ClientMain [config file]");
            System.exit(1);
        }

        parseConfigFile();
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
                    registerUser(register, arguments);
                    break;
                }
                case "login" : break;
                case "logout" : break;
                case "list" : {
                    if (arguments.length != 2 || (!arguments[1].equals("users") && !arguments[1].equals("following"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'list users' or 'list following'");
                        break;
                    }

                    if (arguments[1].equals("users")) { //list users

                    } else { //list following

                    }
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

    //Registers a new user to WINSOME
    private static void registerUser(RegisterInterface register, String[] arguments) {
        if (arguments.length > 8) {
            System.err.println("Usage: register username password tags (max 5 tag)");
            return;
        }

        List<String> tag = new ArrayList<>(Arrays.asList(arguments).subList(3, arguments.length));

        try {
            int result;
            if ((result = register.register(arguments[1], Hash.bytesToHex(Hash.sha256(arguments[2])), tag)) != 0) {
                if (result == 1) System.err.println("Password field is empty");
                if (result == 2 || result == 3) System.err.println("Registration requires minimum 1 tag and maximum 5");
                if (result == 4) System.err.println("User '" + arguments[1] + "' already registered");
            }
        } catch (RemoteException e) {
            System.err.println("Error while registering new user: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Errore while hashing the password: " + e.getMessage());
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
                    //TODO Aggiungere ulteriori case
                }
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
