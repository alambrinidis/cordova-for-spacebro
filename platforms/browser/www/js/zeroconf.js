/* Script to display zeroconf service  */

console.log('You made it so far');

var zeroconf = cordova.plugins.ZeroZonf;

zeroconf.getHostname(function success(hostname){

    console.log('The device\'s hostname is : ' + hostname)

}, function failure(){
    
    console.log('Device\'s hostname could not be retrieved')
});

console.log('Excelsior');
