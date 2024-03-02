import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A server that hosts the Converter service, plus infrastructure services like health and reflection.
 */
public final class ConversionServer {
  public static void main(String[] args) throws IOException, InterruptedException {
	
	// setting Configuration file
	File configurationFile = new File("conversion_parameters_server.txt");
	if (configurationFile.exists() && configurationFile.isFile()) {
		SharedParameters.setAcceptableParameters(configurationFile);
		System.out.println("Configuration file successfully loaded");
	} else {
        System.out.println("Error while reading the configuration file");
	}
	
    Map<String, Integer> acceptableInputParameters = SharedParameters.getAcceptableInputParameters();
    Map<String, Integer> acceptableOutputParameters = SharedParameters.getAcceptableOutputParameters();
    
    // Print acceptable parameters
    System.out.println("Acceptable Input Parameters: " + acceptableInputParameters);
    System.out.println("Acceptable Output Parameters: " + acceptableOutputParameters);
	
    int port = 50051;
    final Server server = ServerBuilder.forPort(port)
        .addService(new Converter())
        .addService(new Registrator())
        .build()
        .start();
    System.out.println("Listening on port " + port);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        server.shutdown();
        try {
          if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
          }
        } catch (InterruptedException ex) {
          server.shutdownNow();
        }
      }
    });
    server.awaitTermination();
  }
}