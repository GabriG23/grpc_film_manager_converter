[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-24ddc0f5d75046c5622901739e7c5dd533143b0c8e959d652212380cedb1ea36.svg)](https://classroom.github.com/a/7vwLpaEp)
# Exam Call 2

The structure of this repository is the following:
  - "NodeClient" contains the node.js ToDoManager service application, which works as a gRPC client (including documentation for REST APIs and JSON schemas);
  - "JavaServer" contains the Java Converter service application, which works as a gRPC server.

Student:
- Gabriele Greco - s303435@studenti.polito.it - [github](https://github.com/GabriG23) <br />

# Main Design Choices and assumptions

## Client
#### Main Folders
- `/api`: folder containing .json and .yaml openapi definition
- `/components`: Film Manager components structures
- `/controllers`: folder containing films, images, reviews and users controllers
- `/database`: database folder
- `/json_schemas`: json schemas of differents components
- `/proto`: folder of the registration and conversion proto files
- `/service`: services of the film manager
- `/uploads`: images uploaded
- `/utils`: utils functions plus configuration file location 'conversion_parameters_client.txt'
#### New APIs and modified ones
- `/api/registration` / Service Registration: send the clientId and the desired parameters. If the clientId is already taken the client will generate a new random ClientId that will be used for the next registration. However, if the client tries to registrate again after a successful registration, the server will refuse the registration due to the clientId already taken.
- `/api/readparameters` / Read Negotiated Parameters: send the clientId to the server that will allow the read only if the client is registered. This will overwrite the parameters present in the negotiated file as they are needed for the other functions.
- `/api/films/public/:filmId/images/:imageId` / addImage: it has been updated with the new constraints and it's composed by two operation. The multer module that upload the image in the folder and the API request that add the image in the database. Both of these functions have been updated to check if the image is in line with the image type and image size negotiated.
- `/api/films/public/:filmId/images` / getImage: the convert api have been updated with input/output checks for image that needs to be sent. These checks are done server-side too.
- `/components/storage.js`: the storage have been modify to handle image file with not supported type. Due to the nature of multer we cannot check the image size here, but only after the creation of the file. Thus the image size will be checked in the AddImage API.
- `/utils/read_file.js`: new file have been added to read the configuration file, to update only the clientId in the file, and to update only the negotiated parameters. All these operations add checks to see if the configuration file has the right format.
- location of the configuration file: `utils/conversion_parameters_client.txt`. There are no shared data structure to save the clientId and negotiated parameters: the client will read his clientID and negotiated parameters from the file at every operation requested.

## Server
- `ConversionServer`: it will read the configuration file and will start the server listening at port `50051`. This server has two service: registration and conversion. 
- `Configuration File`: once the server reads its configuration file with acceptable parameters, it cannot be changed. The file will be read only one time at server start, and if someone modifies the configuration file at runtime, the modification won't affect the currently negotiated parameters. To update the configuration file the server must be offline.<br />
#### Shared Parameters
- It is composed by five data structure. These data are shared between all the service, so concurrency is fundamental. They are managened with concurrent hash map to support concurrent read and write operations in a multithread environment like this one.
* `registeredClients`: list of registered client with their Ids.
* `negotiatedInputParameters`: negotiated input parameters with image type and size for all the clients.
* `negotiatedOutputParameters`: negotiated input parameters with image type and size for all the clients.
* `acceptableInputParameters`: this contains the acceptable input parameters of the server, read from the configuration file.
* `acceptableOutputParameters`: this contains the acceptable output parameters of the server, read from the configuration file.
* I chose to separate input and output data in order to make everything faster and to not have long of services waiting for a resource, therefore different services could access at input or output at different times.
* Moreover I decided to separate registration and converter services to improve scalability and maintaninablity, they are described here:<br />
#### Registration Service
* this service contains two methods that will manage the registration of the clients and the read of the negotiated parameters. Both of these methods has the same response that returns the the negotiated parameters that will be saved on client configuration file.
* The third point of the assignment, 'If the chosen ID is already taken by another registered client, the service will refuse registration,' has been interpreted strictly. The server will simply reject the registration, and it will be the client's responsibility to generate a new ID.
* The reponse message contains a success state, a message, input and output parameters.<br />
* During negotiation it checks available type from server side and assign the minimum of the desired maximum value and the maximum admissible value for each allowed type, cases with size equal to 0 have been managed too, both from the client and server configuration file.
#### Converter Service
* This service allow the conversion of the image given the desired types and dimensions.
* It has the same structure of lab2. I added all the checks for input/output image type and size plus some extra error management.
* In the request message the clientId has been added
* The response remained unchanged.
More informations about each test below.

#### Tests done
- All the APIs have been tested with shutted down server, they will receive a response that will tell them that the server is offline.
- If an user disconnect, it won't lose his clientId and it will be still saved in the configuration file.
- The clientId is always checked on server side for all services
- Both checks of image type and image size are done in both client side and server side based on their respective configuration file.

### Proto files
There are two proto files, one for the Converter service and one for the Registration service
#### conversion.proto
- `fileConvert`: service method for the convertion of an image
- `ConversionRequest`: request message for conversion. It has been added a clientId representing a client
- `MetadataRequest`: request message for the image types of origin and target
- `ConversionReply`: response message for the image
- `MetadataReply`: response message for determining the success of operation
#### registration.proto
- `registerClient`: service method for client registration and parameters negotiation
- `getNegotiatedParameters`: service method for reading the negotiated parameters of the client
- `Parameter_pair`: class for the pair image type and image size
- `ClientRegistrationRequest`: client request for the registration containing clientId and input/output sizes
- `ClientIdRequest`: request message for reading the parameters negotiated
- `ClientRegistrationResponse`: response message both used for registration and for the read of parameters

### Users available for test purpose
EMAIL                           PASSWORD 
- user.dsp@polito.it          - password
- frank.stein@polito.it       - shelley97
- karen.makise@polito.it      - fg204v213
- rene.regeay@polito.it       - historia
- beatrice.golden@polito.it   - seagulls
- arthur.pendragon@polito.it  - holygrail

### How to run the client
- `npm install`: installing all the libraries
- `npm start`: starts the server listening on port 3001 (For the first client)

### How to run the server:
- right click on `project` --> `Maven` --> `Update Project`
- If the `Update Project` is not installing the dependencies from `pom.xml` file and classes from the proto file: right click on project and `Run as` --> `5 Maven Install` and the `Update Project` again
- right click on `ConversionServer.java` and `run on Java application`. The server will be listening on port 50051 for clients requests.
- the server has already been setter for linux in the pom.xml file
 
### Postman
Two collections of postman have been added for test purpose
- `Film Manager - Client 1` listenining on port 3001
- `Film Manager - Client 2` listenining on port 3002<br />
The port switch must be done manually after the first server has started.<br />
Both clients will read from the same configuration file.<br />
In order to test everything the postman collection has been already setted with some APIs.<br />
The code have been tested with windows and linux.<br />
I could not manage to have two client logged at the same time even with different users info in postman and different ports, so I could not check the server behaviour with two clients, but there should not be any major issues.

### Some file format
Client configuration file:

No gif:
```
Client2122321
input PNG 2000
input JPG 4000
output PNG 2000
output JPG 4000
```
No png output type:
```
Client2122321
input PNG 2000
input JPG 4000
output JPG 4000
```
No jpg input type:
```
Client2122321
input JPG 4000
output PNG 2000
output JPG 4000
```
0 max size
```
Client2122321
input PNG 0
input JPG 0
output PNG 2000
output JPG 4000
```
Server configuration file:

everything
```
input PNG 1000
input JPG 5000
input GIF 2000
output PNG 2000
output JPG 4000
output GIF 1500
```
only png type
```
input PNG 1000
output PNG 2000
```
only input
```
input PNG 1000
input JPG 5000
input GIF 2000
```
only output
```
output PNG 2000
output JPG 4000
output GIF 1500
```
