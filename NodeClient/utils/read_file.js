
// Read parameters from Configuration File
var fs = require('fs');
const filePath = 'utils/conversion_parameters_client.txt';      // file path

exports.readConfigurationFile = function() {    // configuaration file
    try {
        var fileContent = fs.readFileSync(filePath, 'utf-8')        // read every rows
        var rows = fileContent.split('\n')
        var clientId = ''
        var inputs = []
        var outputs = []
        rows.forEach((row, index) => {
            var elements = row.trim().split(' ')
            if (index === 0) {                          // the first index will be the client and the others the parameters
                clientId = row.trim()
            } else if (elements[0] === 'input') {
                inputs.push({ img_type: elements[1], img_max_size: parseInt(elements[2]) })
            } else if (elements[0] === 'output') {
                outputs.push({ img_type: elements[1], img_max_size: parseInt(elements[2]) })
            }
        });
        return {clientId, inputs, outputs};
    } catch {
        console.error('Error while reading configuration file');
        return null
    }
}

// Generate and save a unique clientId for the next registration request
exports.updateClientId = function() {                           // update only the file clientID
    try {                                                       // this function is used when the server refuse the registration, so we have to modify only the id
        let file_content = fs.readFileSync(filePath, 'utf-8');
        let rows = file_content.split('\n');
        let newClientId = generateUniqueId();
        let existingParameters = rows.slice(1).join('\n');
        let updated_content = `${newClientId}\n${existingParameters}`;
        fs.writeFileSync(filePath, updated_content, 'utf-8');
    } catch (e) {
        console.error("Could not write on configuration file!", e);
        return;
    }
}

// Write parameters negotiated in the Configuration File
exports.updateNegotiatedParameters = function(inputs, outputs) {    // 
    try {
        let file_content = fs.readFileSync(filePath, 'utf-8');
        let rows = file_content.split('\n');
        let clientId = rows[0]; // take first row
        rows.length = 1;        // remove everything beside the first row
        inputs.forEach(({img_type, img_max_size}) => {          // write again the file with the negotiated parameters
            rows.push(`input ${img_type} ${img_max_size}`);
        });
        outputs.forEach(({img_type, img_max_size}) => {
            rows.push(`output ${img_type} ${img_max_size}`);
        });
        fs.writeFileSync(filePath, rows.join('\n'), 'utf-8');
    } catch (e) {
        console.error("Could not write on configuration file!", e);
    }
}

function generateUniqueId() {   // just a straightforward function to generate a random id, could be done better
    return 'Client' + Math.floor(Math.random() * 100000000);
}