'use strict';

const db = require('../components/db');
const User = require('../components/user');
const Conf = require('../utils/read_file');

const bcrypt = require('bcrypt');

const PROTO_PATH = __dirname + '/../proto/registration.proto';
const REMOTE_URL = "localhost:50051";
const grpc = require('@grpc/grpc-js');               // gRPC Library
const protoLoader = require('@grpc/proto-loader');  // proto loader

let packageDefinition = protoLoader.loadSync(               // load sync
    PROTO_PATH,
    {keepCase: true,
     longs: String,
     enums: String,
     defaults: true,
     oneofs: true
    });
let vs = grpc.loadPackageDefinition(packageDefinition).registration;                  // package definition
let client = new vs.Registrator(REMOTE_URL, grpc.credentials.createInsecure());

/**
 * Retrieve a user by his email
 * Input:
 * - email: email of the user
 * Output:
 * - the user having the specified email
 */
 exports.getUserByEmail = function (email) {
  return new Promise((resolve, reject) => {
      const sql = "SELECT * FROM users WHERE email = ?";
      db.all(sql, [email], (err, rows) => {
          if (err) 
              reject(err);
          else if (rows.length === 0)
              resolve(undefined);
          else{
              const user = createUser(rows[0]);
              resolve(user);
          }
      });
  });
};

/**
 * Retrieve a user by his ID 
 * Input:
 * - id: ID of the user
 * Output:
 * - the user having the specified ID
 */
exports.getUserById = function (id) {
  return new Promise((resolve, reject) => {
      const sql = "SELECT id, name, email FROM users WHERE id = ?"
      db.all(sql, [id], (err, rows) => {
          if (err) 
              reject(err);
          else if (rows.length === 0)
              resolve(undefined);
          else{
              const user = createUser(rows[0]);
              resolve(user);
          }
      });
  });
};

/**
 * Retrieve all the users
 * Input:
 * - none
 * Output:
 * - the list of all the users
 */
exports.getUsers = function() {
    return new Promise((resolve, reject) => {
        const sql = "SELECT id, name, email FROM users";
        db.all(sql, [], (err, rows) => {
            if (err) {
                reject(err);
            } else {
                if (rows.length === 0)
                     resolve(undefined);
                else {
                    let users = rows.map((row) => createUser(row));
                    resolve(users);
                }
            }
        });
      });
  }

/**
 * Logs a user in or out
 * The user who wants to log in or out sends the user data to the authenticator which performs the operation.
 * body User The data of the user who wants to perform log in or log out. For login the structure must contain email and password. For logout, the structure must contain the user id.
 * type String The operation type (\"login\" or \"logout\") (optional)
 * no response value expected for this operation
 **/
 exports.authenticateUser = function(body,type) {
  return new Promise(function(resolve, reject) {
    resolve();
  });
}

/**
 * Negotiate with server the clientId and the parameters for conversion 
 **/
exports.serviceRegistration = function() {

    return new Promise((resolve, reject) => {

        // Read the parameters from configuration file
        var result = Conf.readConfigurationFile();
        if (result == null) {
            reject(404);
        }
        let clientId = result.clientId
        let desired_input_parameters = result.inputs
        let desired_output_parameters = result.outputs
    
        // Open the gRPC call with the gRPC server
        const request = {'client_id': clientId, 'desired_input_parameters': desired_input_parameters, 'desired_output_parameters': desired_output_parameters}       
        client.registerClient(request, (err, response) => {

            let success = false;
            if (response != undefined) {
                try {
                    success = response.success;
                    if(success == false) {                      // ClientId already taken
                        Conf.updateClientId();               // Update the clientId in the configuration file
                        console.log(response.message);      
                        reject(401) // not authorized
                    } else if (success == true) {                      // Registration was successful
                        if(response.negotiated_input_parameters != undefined && response.negotiated_output_parameters != undefined) {
                            Conf.updateNegotiatedParameters(response.negotiated_input_parameters, response.negotiated_output_parameters)
                            console.log(response.message);      
                            resolve();  // success
                        } else {
                            console.log('Something went wrong with response parameters!');      
                            reject(500) // not authorized
                        }
                    }
                } catch(e) {
                    console.error('Something went wrong with response parameters!', e);
                    reject(500);
                }
            } else {
                console.error('Response not received! Server may be offline.');
                reject(500);
            }
        });
    });
}

exports.readConversionParameters = function () {

    return new Promise((resolve, reject) => {

        // Read the parameters from configuration file
        var result = Conf.readConfigurationFile();
        if (result == null) {
            reject(404);
        }
        let clientId = result.clientId;

        const request = {'client_id': clientId};
        client.getNegotiatedParameters(request, (err, response) => {

            let success = false;
            if (response != undefined) {
                try {
                    success = response.success;
                    if(success == false) {                      // ClientId already taken
                        console.log(response.message);      
                        reject(401) // not authorized
                    } else if (success == true) {                      // Registration was successful
                        if(response.negotiated_input_parameters != undefined && response.negotiated_output_parameters != undefined) {
                            // update the read parameter in configuration file
                            Conf.updateNegotiatedParameters(response.negotiated_input_parameters, response.negotiated_output_parameters)
                            console.log(response.message);      
                            resolve() // success
                        } else {
                            console.log('Something went wrong with response parameters!');      
                            reject(500) // response error
                        }
                    }
                } catch(e) {
                    console.error('Something went wrong with response parameters!', e);
                    reject(500);
                }
            } else {
                console.error('Response not received! Server may be offline.');
                reject(500);
            }
        });
    });
}

/**
 * Utility functions
 */

const createUser = function (row) {
  const id = row.id;
  const name = row.name;
  const email = row.email;
  const hash = row.hash;
  return new User(id, name, email, hash);
}

exports.checkPassword = function(user, password){
  let hash = bcrypt.hashSync(password, 10);
  return bcrypt.compareSync(password, user.hash);
}