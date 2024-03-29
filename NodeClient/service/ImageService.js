'use strict';

const { cachedDataVersionTag } = require('v8');
const db = require('../components/db');
const Image = require('../components/image');
const Conf = require('../utils/read_file');

const PROTO_PATH = __dirname + '/../proto/conversion.proto';
const REMOTE_URL = "localhost:50051";
const grpc = require('@grpc/grpc-js');               // gRPC Library
const protoLoader = require('@grpc/proto-loader');  // proto loader
var fs = require('fs');

let packageDefinition = protoLoader.loadSync(               // load sync
    PROTO_PATH,
    {keepCase: true,
     longs: String,
     enums: String,
     defaults: true,
     oneofs: true
    });
let vs = grpc.loadPackageDefinition(packageDefinition).conversion;                  // package definition
let client = new vs.Converter(REMOTE_URL, grpc.credentials.createInsecure());

/**
 * Add a new image to the film
 **/
exports.addImage = function(filmId, file, owner) {

    return new Promise((resolve, reject) => {

        const sql1 = "SELECT owner FROM films f WHERE f.id = ? AND f.private = 0";
        db.all(sql1, [filmId], (err, rows) => {
            if (err)
                reject(err);
            else if (rows.length === 0)
                reject(404);
            else if(owner != rows[0].owner) {
                reject(403);
            }
            else {
                if (!file) {
                    console.log('File have been removed due to not accepted type or size!')
                    reject(404);
                } else {                
                    var nameFile = file.filename;
                    var nameWithoutExtension = nameFile.substring(0, nameFile.lastIndexOf(".") );
                    var extension = nameFile.substring(nameFile.lastIndexOf(".")).replace('.', '');

                    // Read parameters from Configuration File
                    var result = Conf.readConfigurationFile();
                    if (result == null) {
                        reject(404);
                    }
                    var desired_input_parameters = result.inputs
                    
                    // Check if the image meets the specification negotiated
                    var acceptedImgTypes = desired_input_parameters.map((e) => e.img_type.toUpperCase());
                    if(!acceptedImgTypes.includes(extension.toUpperCase())){
                        console.log("File type not supported!")
                        fs.unlink(file.path, (err) => {
                            if (err) {
                            console.error(`Error deleting image: ${nameFile}`, err);
                            } else {
                            console.log(`Image deleted: ${nameFile}`);
                            }
                        });
                        reject(415);    // Mime type not supported
                    } else {
                        let element = desired_input_parameters.find((elem) => elem.img_type.toUpperCase() === extension.toUpperCase()) // get image size supported in KB
                        let max_img_size = element.img_max_size * 1024;   // file limit in bytes
                        if (file.size > max_img_size && max_img_size != 0) {
                            console.log("File size exceeds the accepted size!")
                            fs.unlink(file.path, (err) => {
                                if (err) {
                                console.error(`Error deleting image: ${nameFile}`, err);
                                } else {
                                console.log(`Image deleted: ${nameFile}`);
                                }
                            });
                            reject(413) // Payload too large
                        }
                    }

                    // SQL query for the creation of the image
                    const sql2 = 'INSERT INTO images(name) VALUES(?)';
                    db.run(sql2, [nameWithoutExtension], function(err) {
                        if (err) {
                            reject(err);
                        } else {
                            var imageId = this.lastID;
                            var imageInstance = new Image(imageId, filmId, nameWithoutExtension);
                            // SQL query to associate the image to the film

                            const sql3 = 'INSERT INTO filmImages(filmId, imageId) VALUES(?, ?)';
                            db.run(sql3, [filmId, imageId], function(err) {
                                if (err) {
                                reject(err);
                                } else {
                                resolve(imageInstance);
                                }
                            });
                        }
                    });
             }
            }
        });  
    });
  }

/**
 * Retrieve the images associated to the film with ID filmId
 **/
exports.getImages = function(filmId, owner) {
    return new Promise((resolve, reject) => {

        const sql1 = "SELECT f.owner as owner, r.reviewerId as reviewer FROM films f, reviews r WHERE f.id = ? AND f.private = 0 AND f.id = r.filmId";
        db.all(sql1, [filmId], (err, rows) => {
            if (err)
                reject(err);
            else if (rows.length === 0)
                reject(404);
            else {

                var forbidden = true;
                for (var i = 0; i < rows.length; i++) {
                    if(owner == rows[i].owner || owner == rows[i].reviewer){
                        forbidden = false;
                    }
                }

                if(forbidden){
                    reject(403);
                }
                else
                {
                    const sql2 = 'SELECT imageId, name FROM images as i, filmImages as f WHERE i.id = f.imageId AND f.filmId = ?';
                    db.all(sql2, [filmId], function(err, rows) {
                        if (err)
                            reject(err);
                        else {
                            let images = rows.map((row) => new Image(row.imageId, filmId, row.name));
                            resolve(images);
                        }
                    });
                }
            }
        }); 
    });
  }

/**
 * Retrieve an image data structure
 **/
exports.getSingleImage = function(imageId, imageType, filmId, owner) {
    return new Promise((resolve, reject) => {

        const sql1 = "SELECT f.owner as owner, r.reviewerId as reviewer FROM films f, reviews r WHERE f.id = ? AND f.private = 0 AND f.id = r.filmId";
        db.all(sql1, [filmId], (err, rows) => {
            if (err)
                reject(err);
            else if (rows.length === 0)
                reject(404);
            else {

                var forbidden = true;
                for (var i = 0; i < rows.length; i++) {
                    if(owner == rows[i].owner || owner == rows[i].reviewer){
                        forbidden = false;
                    }
                }

                if(forbidden){
                    reject(403);
                }
                else
                {
                    // SQL query for retrieving the imageName and finding if the image exists for that film
                    const sql2 = 'SELECT name FROM images as i, filmImages as f WHERE i.id = f.imageId AND i.id = ? AND f.filmId = ?';
                    db.all(sql2, [imageId, filmId], async function(err, rows) {
                        if (err)
                            reject(err);
                        else if (rows.length === 0)
                            resolve(404);
                        else {
                            //CASE 1: application/json
                            if(imageType == 'json'){
                                var imageInstance = new Image(imageId, filmId, rows[0].name);
                                resolve(imageInstance);
                            }
                            //Case 2: image/png, image/jpg, image/gif
                            else {

                                var nameNoExtension = rows[0].name;
                                
                                //add the extension
                                var nameFile = nameNoExtension + '.' + imageType;
                                var pathFile = __dirname + '/../uploads/' + nameFile;
                                
                                //check if there is a file saved with the requested media type
                                try {
                                    if (fs.existsSync(pathFile)) {
                                        //send the file back
                                        resolve(nameFile);
                                    }  
                                    
                                    else {
        
                                        //otherwise, I must convert the file
                                        //I search for a file, with a different extension, saved server-side
                                        var imageType2, imageType3;
                                        if(imageType == 'png'){
                                            imageType2 = 'jpg';
                                            imageType3 = 'gif'
                                        } else if(imageType == 'jpg'){
                                            imageType2 = 'png';
                                            imageType3 = 'gif'
                                        } else if(imageType == 'gif'){
                                            imageType2 = 'jpg';
                                            imageType3 = 'png'
                                        } 
        
                                        var pathFile2 = './uploads/' + nameNoExtension + '.' + imageType2;
                                        var pathFile3 = './uploads/' + nameNoExtension + '.' + imageType3;
                                        var pathOriginFile = null;
                                        var originType = null;
                                        var pathTargetFile = './uploads/' + nameFile;
                                        
                                        try {
                                            if (fs.existsSync(pathFile2)) {
                                                pathOriginFile = pathFile2;
                                                originType = imageType2;
                                            } else if(fs.existsSync(pathFile3)){
                                                pathOriginFile = pathFile3;
                                                originType = imageType3;
                                            }
                                        } catch(err) {
                                            reject(err);
                                        }
        
                                        if(pathOriginFile == null){
                                            resolve(404);
                                        }
        
                                        await convertImage(pathOriginFile, pathTargetFile, originType, imageType);
                                        resolve(nameFile);
        
                                        }
                                } catch(err) {
                                    reject(err);
                                }
                            }
                        }
                    });
                }
            }
        }); 
    });
  }


/**
 * Delete an image from the film
 **/
exports.deleteSingleImage = function(filmId, imageId, owner) {
    return new Promise((resolve, reject) => {

        const sql1 = "SELECT owner FROM films f WHERE f.id = ? AND f.private = 0";
        db.all(sql1, [filmId], (err, rows) => {
            if (err)
                reject(err);
            else if (rows.length === 0)
                reject(404);
            else if(owner != rows[0].owner) {
                reject(403);
            }
            else {
                //I retrieve the image name
                const sql2 = 'SELECT name FROM images WHERE id = ?';
                db.all(sql2, [imageId], (err, rows) => {
                    if(err)
                        reject(err);
                    else if (rows.length === 0)
                        reject(404);
                    else {
                        var nameNoExtension = rows[0].name;
                        //DELETE
                        //firstly, I delete the relationship with the film 
                        const sql3 = 'DELETE FROM filmImages WHERE filmId = ? AND imageId = ?';
                        db.run(sql3, [filmId, imageId], (err) => {
                            if (err)
                                reject(err);
                            //secondly, I delete the image row from the database
                            else {
                                const sql4 = 'DELETE FROM images WHERE id = ?';
                                db.run(sql4, [imageId], (err) => {
                                    if (err)
                                        reject(err);
                                    //thirdly, I delete the images from the server
                                    else {
                                        var pathFile1 = './uploads/' + nameNoExtension + '.png';
                                        var pathFile2 = './uploads/' + nameNoExtension + '.jpg';
                                        var pathFile3 = './uploads/' + nameNoExtension + '.gif';
                                        if (fs.existsSync(pathFile1)) {
                                            fs.unlinkSync(pathFile1);
                                        }  
                                        if (fs.existsSync(pathFile2)) {
                                            fs.unlinkSync(pathFile2);
                                        }  
                                        if (fs.existsSync(pathFile3)) {
                                            fs.unlinkSync(pathFile3);
                                        }  
                                        resolve();
                                    }
                                });
                        }
                    });
                    }
                });
            }
        }); 
      });
}

function convertImage(pathOriginFile, pathTargetFile, originType, targetType) {

    return new Promise((resolve, reject) => {
        // before opening the connection we check if the input/output type and input size are in line with the parameters
        // output size is not checked
        // Read parameters from Configuration File
        var result = Conf.readConfigurationFile();
        if (result == null) {
            reject(404);        // configuration file error
        } // input JPG 3000
        var clientId = result.clientId
        var desired_input_parameters = result.inputs
        var desired_output_parameters = result.outputs

        // Check type and size of input image. This check will be done on server too
        var inputImageType = originType;
        var outputImageType = targetType;
        var inputImageSize = fs.statSync(pathOriginFile).size;

        var acceptedImgTypes_i = desired_input_parameters.map((e) => e.img_type.toUpperCase());
        var acceptedImgTypes_o = desired_output_parameters.map((e) => e.img_type.toUpperCase());
        if((!acceptedImgTypes_i.includes(inputImageType.toUpperCase())) || (!acceptedImgTypes_o.includes(outputImageType.toUpperCase()))){
            console.log("Input or output file type not supported!")
            reject(415);    // Mime type not supported
        } else {
            let element = desired_input_parameters.find((elem) => elem.img_type.toUpperCase() === inputImageType.toUpperCase()) // get image size supported in KB
            let max_img_size = element.img_max_size * 1024;   // file limit in bytes
            if (inputImageSize > max_img_size && max_img_size != 0) {
                console.log("arrivato qui")
                console.log("Input file size exceeds the accepted size!")
                reject(413) // Payload too large
            } else {
                // Open the gRPC call with the gRPC server
                let call = client.fileConvert();

                call.on('status', function(status) {
                    if (status.code === grpc.status.OK) {
                        console.log('gRPC call was successful');
                    } else {
                        console.error('Error in gRPC call connection');
                        reject(500);
                    }
                });


                // Set callback to receive back the file
                var wstream = fs.createWriteStream(pathTargetFile); //for now, the name is predefined
                var success = false;
                var error = "";

                call.on('data', function(data){
                    //receive meta data
                    if(data.meta != undefined){
                        success = data.meta.success;
                        if(success == false){
                            error = data.meta.error;
                            reject(error);
                        }
                    }
                    //receive file chunck
                    if(data.file != undefined){
                        wstream.write(data.file);
                    }

                });

                // Set callback to end the communication and close the write stream 
                call.on('end',function(){
                    //console.log('in fine')
                    wstream.end();
                })
                            
                // Send the conversion types for the file (when the gRPC client is integrated with the server of Lab01, the file_type_origin and file_type_target will be chosen by the user)
                call.write({ "meta": {"client_id": clientId, "file_type_origin": originType, "file_type_target": targetType}});

                // Send the file
                const max_chunk_size = 1024; //1KB
                const imageDataStream = fs.createReadStream(pathOriginFile, {highWaterMark: max_chunk_size});
            
                imageDataStream.on('data', (chunk) => {
                    call.write({"file": chunk });
                });

                // When all the chunks of the image have been sent, the clients stops to use the gRPC call from the sender side
                imageDataStream.on('end', () => {
                    call.end();
                });

                // Only after the write stream is closed,the promise is resolved (otherwise race conditions might happen)
                wstream.on('close',function() {
                    resolve();
                })

                // error management
                call.on('error', (err) => {
                    // even when the conversion file he stills create a file with size 0, so we are deleting it.
                    fs.unlink(pathTargetFile, (err) => {
                        if (err) {
                          console.error('Error deleting image.', err);
                        } else {
                          console.log('Image deleted.');
                        }
                      });
                    wstream.end();
                    call.end();
                    console.error('Error during file conversion.');
                });

            }
        }
    });
}
