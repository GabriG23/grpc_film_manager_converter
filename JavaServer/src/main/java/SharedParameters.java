import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;

public class SharedParameters {
	/*
	Concurrent shared data structures between all services
	registeredClients: list of Id of all registered clients
	negotiatedInputParameters: all the clients registered with their Id and their negotiated parameters
	negotiatedOutputParameters: all the clients registered with their Id and their negotiated parameters 
	acceptableInputParameters: acceptable input image size for each image type
	acceptableOutputParameters: acceptable output image size for each image type
	*/
    private static final Set<String> registeredClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentHashMap<String, Map<String, Integer>> negotiatedInputParameters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, Integer>> negotiatedOutputParameters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> acceptableInputParameters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> acceptableOutputParameters = new ConcurrentHashMap<>();

    public static boolean getRegisteredClients(String clientId) {
    	boolean containsId = registeredClients.contains(clientId);
    	return containsId;
    }	// return true if the clientId is in the registeredClients
    
    public static Map<String, Integer> getNegotiatedInputParameters(String clientId) {
        return negotiatedInputParameters.get(clientId);
    }	// return the negotiated input type and size of a clientId
    
    public static Map<String, Integer> getNegotiatedOutputParameters(String clientId) {
        return negotiatedOutputParameters.get(clientId);
    }	// return the negotiated output type and size of a clientId
   
    public static ConcurrentHashMap<String, Integer> getAcceptableInputParameters() {
        return acceptableInputParameters;
    }	// return acceptable input type and size parameters read from the configuration file
    
    public static ConcurrentHashMap<String, Integer> getAcceptableOutputParameters() {
        return acceptableOutputParameters;
    }	// return acceptable output type and size parameters read from the configuration file
    
    public static void setRegisteredClients(String clientId) {
    	registeredClients.add(clientId);
    }	// add the client the the registered clients list
    
    public static void setNegotiatedInputParameters(String clientId, Map<String, Integer> parameters) {
    	negotiatedInputParameters.put(clientId, parameters);
    }	// add the negotiated input parameters for a new client
    
    public static void setNegotiatedOutputParameters(String clientId, Map<String, Integer> parameters) {
    	negotiatedOutputParameters.put(clientId, parameters);
    }	// add the negotiated output parameters for a new client
    
    public static void setAcceptableParameters(File fileName) {
    	// read the configuration file parameters
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String row;
            while ((row = br.readLine()) != null) {
                String[] rows = row.split(" ");
                if (rows.length == 3) {
                    String ioType = rows[0].toLowerCase();
                    String imgType = rows[1];
                    Integer maxSize = Integer.parseInt(rows[2]);
                    
                    if ("input".equals(ioType)) {
                        acceptableInputParameters.put(imgType.toUpperCase(), maxSize);
                    } else if ("output".equals(ioType)) {
                        acceptableOutputParameters.put(imgType.toUpperCase(), maxSize);
                    } else {
                        System.out.println("Invalid ioType: " + ioType);
                    }
                } else {
                    System.out.println("Invalid row format: " + row);
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
