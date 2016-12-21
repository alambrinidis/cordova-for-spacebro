cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
    {
        "file": "plugins/cordova-plugin-zeroconf/www/zeroconf.js",
        "id": "cordova-plugin-zeroconf.ZeroConf",
        "pluginId": "cordova-plugin-zeroconf",
        "clobbers": [
            "cordova.plugins.zeroconf"
        ]
    }
];
module.exports.metadata = 
// TOP OF METADATA
{
    "cordova-plugin-add-swift-support": "1.6.0",
    "cordova-plugin-console": "1.0.5",
    "cordova-plugin-whitelist": "1.3.1",
    "cordova-plugin-zeroconf": "1.2.1"
}
// BOTTOM OF METADATA
});