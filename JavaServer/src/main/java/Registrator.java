import registration.*;
import registration.RegistratorGrpc.RegistratorImplBase;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Registrator extends RegistratorImplBase {

	private static final Logger logger = Logger.getLogger(Registrator.class.getName());
	
	@Override
	public synchronized void registerClient(ClientRegistrationRequest request, StreamObserver<ClientRegistrationResponse> responseObserver) {
		// Service for Registration of ClientId
		String clientId = request.getClientId();				// get the Id
        System.out.println("ClientId: " + clientId.toString());
        if (SharedParameters.getRegisteredClients(clientId)) {	// check if the Id already exists
			// The clientId is already taken, refuse registration
			logger.log(Level.INFO, "The clientId is not available, try again!");
			// onNext send the response
			responseObserver.onNext(ClientRegistrationResponse.newBuilder()
					.setSuccess(false)
					.setMessage("Client ID already taken!")
					.build());
	        responseObserver.onCompleted();
	        return;
        } else {
    		try {
	        	// Parameters Negotiation
	        	SharedParameters.setRegisteredClients(clientId);	// save clientId
	        	
	            Map<String, Integer> negotiatedInputParams = negotiateParameters(	// negotiate input parameters
	            		clientId,
	                    request.getDesiredInputParametersList(),
	                    SharedParameters.getAcceptableInputParameters());
	        	
	            Map<String, Integer> negotiatedOutputParams = negotiateParameters(	// negotiate output parameters
	                    clientId,
	                    request.getDesiredOutputParametersList(),
	                    SharedParameters.getAcceptableOutputParameters());
	        	
	            // Save negotiated input/output parameters for the client
	            SharedParameters.setNegotiatedInputParameters(clientId, negotiatedInputParams);
	            SharedParameters.setNegotiatedOutputParameters(clientId, negotiatedOutputParams);
				// return the registration and parameters
	            logger.log(Level.INFO, "Registration completed and parameters successfully negotiated!");
	            responseObserver.onNext(ClientRegistrationResponse.newBuilder()
	                    .setSuccess(true)
	                    .setMessage("Registration completed and parameters successfully negotiated!")
	                    .addAllNegotiatedInputParameters(convertMapToParametersList(negotiatedInputParams))
	                    .addAllNegotiatedOutputParameters(convertMapToParametersList(negotiatedOutputParams))
	                    .build());
	            responseObserver.onCompleted();
	            return;
        	} catch (Exception e) {	// see below for error check
	          	  logger.log(Level.INFO,"Error during client registration!", e);
	              handleRegistrationError(e);
	              responseObserver.onError(e);
				  return;
        	}
		}
	}
	
	// Service read negotiated parameters
    @Override
	public synchronized void getNegotiatedParameters(ClientIdRequest request, StreamObserver<ClientRegistrationResponse> responseObserver) {
		// Read the negotiated parameters from the Server
		try {
			String clientId = request.getClientId();				// get the Id
            System.out.println("ClientId: " + clientId);
	        if (SharedParameters.getRegisteredClients(clientId)) {	// check if the Id already exists
				// The client exists, return his parameters
	        	Map<String, Integer> negotiatedInputParams = SharedParameters.getNegotiatedInputParameters(clientId);
	        	Map<String, Integer> negotiatedOutputParams = SharedParameters.getNegotiatedOutputParameters(clientId);
	            
	            System.out.println(negotiatedInputParams);
	            System.out.println(negotiatedOutputParams);
	            // return parameters, read success
	        	logger.log(Level.INFO, "Read of parameters completed!");
	            responseObserver.onNext(ClientRegistrationResponse.newBuilder()
	                    .setSuccess(true)
	                    .setMessage("Negotiated Parameters read successfully!")
	                    .addAllNegotiatedInputParameters(convertMapToParametersList(negotiatedInputParams))
	                    .addAllNegotiatedOutputParameters(convertMapToParametersList(negotiatedOutputParams))
	                    .build());
	            responseObserver.onCompleted();
	        } else {	// client not registers
	        	logger.log(Level.INFO, "Cannot read parameters, clientId is not registered!");
	            responseObserver.onNext(ClientRegistrationResponse.newBuilder()
	                    .setSuccess(false)
	                    .setMessage("Cannot read parameters, clientId is not registered!")
	                    .build());
	            responseObserver.onCompleted();
			}
	        return;
		} catch (Exception e) {	// see below 
        	  logger.log(Level.INFO,"Error during parameter read!", e);
              handleRegistrationError(e);
              responseObserver.onError(e);
		}
	}
    
    private Map<String, Integer> negotiateParameters(String clientId, List<Parameter_pair> desiredParameters, Map<String, Integer> acceptableParameters) {
        // negotiate the parameters with the acceptable parameters of the server service
    	Map<String, Integer> negotiatedParams = new HashMap<>();		// negotiated parameters
        for (Parameter_pair desiredParam : desiredParameters) {			// loop between pairs type and size
            String imgType = desiredParam.getImgType();
            int desiredMaxSize = desiredParam.getImgMaxSize();
            if (acceptableParameters.containsKey(imgType)) {			// check on type
                int maxAdmissibleSize = acceptableParameters.get(imgType);
                int negotiatedSize = Math.min(desiredMaxSize, maxAdmissibleSize);
                if (desiredMaxSize == 0) {				// check if the size are 0 (no size limit)
                	negotiatedSize = maxAdmissibleSize;
                } else {
                	if (maxAdmissibleSize == 0) {
                		negotiatedSize = desiredMaxSize;
                	}	
                }
                negotiatedParams.put(imgType, negotiatedSize);
            }
        }
        return negotiatedParams;
    }

    private List<Parameter_pair> convertMapToParametersList(Map<String, Integer> parameterMap) {
    	// convert the negotiated parameters into a list for the response message
        List<Parameter_pair> parametersList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : parameterMap.entrySet()) {
            parametersList.add(Parameter_pair.newBuilder()
                    .setImgType(entry.getKey())
                    .setImgMaxSize(entry.getValue())
                    .build());
        }
        return parametersList;
    }

    private void handleRegistrationError(Exception e) {	// various errors check, similar to the converter services
        if (e instanceof StatusRuntimeException) {
            StatusRuntimeException statusException = (StatusRuntimeException) e;
            if (statusException.getStatus().getCode() == Status.CANCELLED.getCode()) {
                logger.log(Level.INFO, "Client canceled the request.");
            } else if (statusException.getStatus().getCode() == Status.UNKNOWN.getCode()) {
                logger.log(Level.INFO, "Unknown error in registration!", e);
            } else {
                logger.log(Level.INFO, "Error in registration! Status: " + statusException.getStatus(), e);
            }
        } else {
            logger.log(Level.INFO, "Unexpected error in registration!", e);
        }
    }
	
}