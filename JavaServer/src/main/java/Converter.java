import conversion.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import com.google.protobuf.ByteString;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

public final class Converter extends ConverterGrpc.ConverterImplBase {

	private static final Logger logger = Logger.getLogger(Converter.class.getName());
	
	@Override
	public synchronized StreamObserver<ConversionRequest> fileConvert(final StreamObserver<ConversionReply> responseObserver) {
			    
	    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    final AtomicBoolean completed = new AtomicBoolean(false);
	    final StringBuffer typeOrigin = new StringBuffer("");
	    final StringBuffer typeTarget = new StringBuffer("");
	    final StringBuffer clientId = new StringBuffer("");
	    final StringBuffer errorMessage = new StringBuffer("");
	    final AtomicBoolean success = new AtomicBoolean(true);

	    return new StreamObserver<ConversionRequest>() {
	    	  @Override
	    	  public void onNext(ConversionRequest dataChunk) {
	    		  
	    		  if(success.get()) {
	    			  try {
	    				  switch(dataChunk.getRequestOneofCase().getNumber()) {
	    				  	  //meta data information is received
			            	  case ConversionRequest.META_FIELD_NUMBER : {
			            		  clientId.append(dataChunk.getMeta().getClientId());
			            		  typeOrigin.append(dataChunk.getMeta().getFileTypeOrigin());
			            		  typeTarget.append(dataChunk.getMeta().getFileTypeTarget());
			            	  }
			            	  //file chunk is received
			            	  case ConversionRequest.FILE_FIELD_NUMBER : {
				                     baos.write(dataChunk.getFile().toByteArray());
			            	  }	
	            		  }
	            	  } catch (IOException e) {
	                      logger.log(Level.INFO,"error on write to byte array stream!", e);
	                      onError(e);
	                  } catch(Exception e) {
	                	  logger.log(Level.INFO,"error on receiving the file!", e);
	                      onError(e);
	                  }
            	  }
              }

              @Override
              public void onError(Throwable t) {
            	  if (t instanceof StatusRuntimeException) {
            		    StatusRuntimeException statusException = (StatusRuntimeException) t;
            		    if (statusException.getStatus().getCode() == Status.CANCELLED.getCode()) {
            		        // Handle cancellation error, e.g., clean up resources
            		        logger.log(Level.INFO, "Client canceled the request.");
            		    } else if (statusException.getStatus().getCode() == Status.UNKNOWN.getCode()) {
            		        // Handle unknown error
            		        logger.log(Level.INFO, "Unknown error in receiving the file!", t);
            		    } else {
            		        // Handle other status codes
            		        logger.log(Level.INFO, "Error in receiving the file! Status: " + statusException.getStatus(), t);
            		    }
            		} else {
            		    logger.log(Level.INFO, "Unexpected error in receiving the file!", t);
            		}
                  success.set(false);
              }

              @Override
              public void onCompleted() {
                  logger.log(Level.INFO, "File has been received!");
                  completed.compareAndSet(false, true);
                  
                  // check if the client is registered
                  //String clientId = ConversionRequest.getClientId();
                  System.out.println("ClientId: " + clientId.toString());
                  System.out.println("Conversion: " + typeOrigin.toString().toUpperCase() + " ---> " + typeTarget.toString().toUpperCase());
                  if (!SharedParameters.getRegisteredClients(clientId.toString())) {  				// 1ST CHECK ***********************************************************************************************************
    	        	  logger.log(Level.INFO, "Client not registered!");
    	              success.set(false);
    	              errorMessage.append("Client not registered! You are not allowed to the Conversion Service.");
    				  responseObserver.onNext(ConversionReply.newBuilder()
    			              .setMeta(MetadataReply.newBuilder()
    			              .setSuccess(false)
    			              .setError(errorMessage.toString()))
    			              .build());
                  } else {
                	  // Get negotiated parameters for the client
                      Map<String, Integer> negotiatedInputParams = SharedParameters.getNegotiatedInputParameters(clientId.toString());
                      Map<String, Integer> negotiatedOutputParams = SharedParameters.getNegotiatedOutputParameters(clientId.toString());
                                      
	                  // check if the input type is available
	                  if(!negotiatedInputParams.containsKey(typeOrigin.toString().toUpperCase())) { 	// 2ND CHECK ***********************************************************************************************************
	    	        	  logger.log(Level.INFO, "Input image type not supported!");	
	    	              success.set(false);
	    	              errorMessage.append("Input image type not supported!");
	    				  responseObserver.onNext(ConversionReply.newBuilder()
	    			              .setMeta(MetadataReply.newBuilder()
	    			              .setSuccess(false)
	    			              .setError(errorMessage.toString()))
	    			              .build());
	                  }
	                  // check if the output type is available
	                  if(!negotiatedOutputParams.containsKey(typeTarget.toString().toUpperCase())) { 	// 3RD CHECK ***********************************************************************************************************
	    	        	  logger.log(Level.INFO, "Output image type not supported!");
	    	              success.set(false);
	    	              errorMessage.append("Output image type not supported!");
	    				  responseObserver.onNext(ConversionReply.newBuilder()
	    			              .setMeta(MetadataReply.newBuilder()
	    			              .setSuccess(false)
	    			              .setError(errorMessage.toString()))
	    			              .build());
	                  }
	                  // REMOVE THIS CHECK, we don't need it since we have configuration file types
	                  // check if media types are supported								// 4TH CHECK ***********************************************************************************************************
	                  /*if(!typeOrigin.toString().equalsIgnoreCase("png") && !typeOrigin.toString().equalsIgnoreCase("jpg")
	    	        		  && !typeOrigin.toString().equalsIgnoreCase("gif") && !typeTarget.toString().equalsIgnoreCase("png") && !typeTarget.toString().equalsIgnoreCase("jpg") 
	    	        		  && !typeTarget.toString().equalsIgnoreCase("gif") 
	    	        		  ) {
	    	        	  logger.log(Level.INFO, "media type not supported!");
	    	        	  success.set(false);
	    	          }*/
	                  
	                  //conversion
	    	          ByteArrayOutputStream baosImageToSend = new ByteArrayOutputStream();
	    	          if(success.get()) {
	    				  try {
	    					  byte[] bytes_input = baos.toByteArray();
	    					  
	                          Integer maxInputSize = negotiatedInputParams.get(typeOrigin.toString().toUpperCase());

	                          // check the size, if it is equal to 0, there's no size limit             
	    					  if(maxInputSize > 0) { 									// 5TH CHECK ***********************************************************************************************************
		                          long maxSizeBytesInput = maxInputSize * 1024;
		                          System.out.println("Image size limit: " + maxSizeBytesInput);
		                          System.out.println("Image size: " + bytes_input.length);
	    						  if(bytes_input.length > maxSizeBytesInput) {			// 6TH CHECK ***********************************************************************************************************
	    	        	        	  logger.log(Level.INFO, "Input image size not acceptable!");
	    	        	              success.set(false);
	    	        	              errorMessage.append("Input image size not acceptable!");
	    		    				  responseObserver.onNext(ConversionReply.newBuilder()
	    		    			              .setMeta(MetadataReply.newBuilder()
	    		    			              .setSuccess(false)
	    		    			              .setError(errorMessage.toString()))
	    		    			              .build());
	    						  }
	    					  }
	    						  
	    				      if(success.get()) {
		    				      ByteArrayInputStream bais = new ByteArrayInputStream(bytes_input);
		    				      BufferedImage imageReceived = ImageIO.read(bais);
		    					  if(imageReceived.getColorModel().getTransparency() != Transparency.OPAQUE) {
		    						  imageReceived = fillTransparentPixels(imageReceived, Color.WHITE);
		    					  }
		    				      ImageIO.write(imageReceived, typeTarget.toString(), baosImageToSend); 
	    				      }
	    				      
	    				      byte[] bytes_output = baosImageToSend.toByteArray();
	                          Integer maxOutputSize = negotiatedOutputParams.get(typeTarget.toString().toUpperCase());
	                          
	                          // check the size, if it is equal to 0, there's no size limit
	    				      if(maxOutputSize > 0) {									// 7TH CHECK ***********************************************************************************************************
		                          long maxSizeBytesOutput = maxOutputSize * 1024;
		                          System.out.println("Image size limit: " + maxSizeBytesOutput);
		                          System.out.println("Image size: " + bytes_output.length);
	    						  if(bytes_output.length > maxSizeBytesOutput) { 		// 8TH CHECK ***********************************************************************************************************
	    	        	        	  logger.log(Level.INFO, "Output image size not acceptable!");
	    	        	              success.set(false);
	    	        	              errorMessage.append("Output image size not acceptable!");
	    		    				  responseObserver.onNext(ConversionReply.newBuilder()
	    		    			              .setMeta(MetadataReply.newBuilder()
	    		    			              .setSuccess(false)
	    		    			              .setError(errorMessage.toString()))
	    		    			              .build());
	    						  }
	    				      }
	    				  } catch (IOException e) {
	    					success.set(false);
	    					e.printStackTrace();
	    				  }
	    	          }
	    			  //send the image back
	
	    			  //Case 1: success
	    			  if(success.get()) {
	    				  logger.log(Level.INFO, "conversion has been successful!");
	    				  responseObserver.onNext(ConversionReply.newBuilder()
	    			              .setMeta(MetadataReply.newBuilder().setSuccess(true))
	    			              .build());
	    				
	    				  BufferedInputStream bisImageToSend = new BufferedInputStream(new ByteArrayInputStream(baosImageToSend.toByteArray()));
	
	    	              int bufferSize = 1 * 1024; // 1KB
	    	              byte[] buffer = new byte[bufferSize];
	    	              int length;
	    	              try {
	    					  while ((length = bisImageToSend.read(buffer, 0, bufferSize)) != -1) {
	    					      responseObserver.onNext(ConversionReply.newBuilder()
	    					              .setFile(ByteString.copyFrom(buffer, 0, length))
	    					              .build());
	    					  }
	    				  } catch (IOException e) {
	    					  e.printStackTrace();
	    					  responseObserver.onError(e);
	    				  }
	    	              try {
							  bisImageToSend.close();
						  } catch (IOException e) {
							  e.printStackTrace();
							  responseObserver.onError(e);
						  } 
	    			  } else { //Case 2: error
	    				  logger.log(Level.INFO, "conversion has failed!");
	    				  responseObserver.onNext(ConversionReply.newBuilder()
	    			              .setMeta(MetadataReply.newBuilder()
	    			              .setSuccess(false)
	    			              .setError(errorMessage.toString()))
	    			              .build());
	    			  }
                }
    			responseObserver.onCompleted();           
              }
          };     
	  }; 
	
	public static BufferedImage fillTransparentPixels( BufferedImage image, Color fillColor ) {
		int w = image.getWidth();
		int h = image.getHeight();
		BufferedImage image2 = new BufferedImage(w, h, 
		BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image2.createGraphics();
		g.setColor(fillColor);
		g.fillRect(0,0,w,h);
		g.drawRenderedImage(image, null);
		g.dispose();
		return image2;
	}
};