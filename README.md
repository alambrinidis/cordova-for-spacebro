# Zero-List ✨

A mobile tool to browse bonjour/avahi service on your mobile.
It should display :

- Name of the service
- IP of the service that has been found
- PORT of this service
- detailed information should be display when you touch the list

## 🌍 Installation

It runs on Android and iOS

### 📱 iOS

Run:
`cordova run ios`

### 🤖 Android

Run:
`cordova run android`

## 👋 Usage

Just touch the screen and scroll around the services.

## 📦 Dependencies
[cordova-plugin-zeroconf](https://github.com/becvert/cordova-plugin-zeroconf)

## 🕳 Troubleshooting :

The command `phonegap serve [options]` does not work as intended. The environment in which you are running it does not give you full access to native device functionnality.
It is not *equivalent* as installing the app on the device.
It creates a server to host and thus uses the browser version of phonegap. 
It will result in the phonegap plugins being undefined.

Use the command `phonegap/cordova [run/install] [platform]` instead

## ❤️ Contribute

Please follow standard style conventions.
You can modify the source in `www/js/index.js`

Enjoy !