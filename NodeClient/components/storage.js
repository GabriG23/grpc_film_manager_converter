'use strict';

const Conf = require('../utils/read_file');
const multer = require('multer');

// storage of the image
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
      cb(null, './uploads');
    },
  filename: function (req, file, cb) {
      cb(null, file.originalname);
  }
});

// fileFilter is a multer function to control which files should be uploaded and which should be skipped
const fileFilter = function(req, file, cb) {
  // check if the file size and the file type is within the configuration file parameters
  let result = Conf.readConfigurationFile();   // Read configuration file
  if (result == null) {
      console.log('Error during reading of negotiated parameters!')
      cb(null, false) // Reject the image
  } else {
      let desired_input_parameters = result.inputs
      // Check if the image meets the specification negotiated
      let acceptedImgTypes = desired_input_parameters.map((e) => e.img_type.toUpperCase());
      var fileMediaType = file.mimetype.split('/').pop();
      if (fileMediaType.toUpperCase()  === 'JPEG') {
        fileMediaType = 'JPG'
      }
      if(!acceptedImgTypes.includes(fileMediaType.toUpperCase())) {  // check if the image type is supported
          console.log('File type not accepted!')
          cb(null, false)
      } else {
          cb(null, true); 

          /* the file size is available only after the uploading of the file, so this extra check will be done
             in the add image api. */
      }
  }
}
// una sola immagine alla volta, ma c'è anche la possibilità di gestire più immagini alla volta
const uploadImg = multer({
  storage: storage,
  fileFilter: fileFilter
}).single('image');

module.exports.uploadImg = uploadImg;
