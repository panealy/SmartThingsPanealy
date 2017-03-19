/**
 *  Wink Connect Service
 *  Author: panealy@gmail.com
 *  Date: 2017-02-26
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  ==================================================
 *  Based off of Quirky (Connect)
 *  Original Author: todd@wackford.net
 *  Date: 2014-02-15
 *  GitHub: https://github.com/twack/Quirky-Connect
 *  Note: No Copyright message was listed, nor 
 *  ==================================================
 *
 *  TODO:
 *  ==============================================
 *   - can't control MyQ garage (third party BS)
 *      - but look here: http://pastebin.com/raw/F1EVijTq (from https://community.home-assistant.io/t/chamberlain-myq-support/3785/7)
 *        "client_id": "quirky_wink_android_app",
 *        "client_secret": "e749124ad386a5a35c0ab554a4f2c045",
 *		- and this WORKS!
 *		- right now a bit hackish, need to streamline auth process.
 *   - need to queue up updates when configuring app, or we can hit app timeout
 *   - NIMBUS:
 *       update dashboard via desired_state
 *
 *
 *  DONE:
 *  ==============================================
 *   - Recieve events from HTTP GET (for pubnub updates)
 *   - 'Subscribe' via HTTP service (or consider just having NODE JS daemon subscribe and send.
 *
 *  Revision History
 *  ==============================================
 *  2017-02-14 Version 0.1.0	Initial Creation
 *  2017-02-25 Version 0.4.0	Added ability to recieve
 *
 */

import java.text.DecimalFormat
import groovy.json.JsonSlurper

// Wink API stuff
private apiUrl() 			{ "https://api.wink.com/" }
private getVendorName() 	{ "Wink Connect Service" }
private getVendorAuthPath()	{ "https://api.wink.com/oauth2/authorize?" }
private getVendorTokenPath(){ "https://api.wink.com/oauth2/token?" }
private getVendorIcon()		{ "https://www.winkapp.com/assets/mediakit/wink-logo-icon-6d60bd7c38ceb7c3a2f0a93e4c4cd307.png" }
private getAndroidAppClientId() 		{ "quirky_wink_android_app" }  // Android client ID [note these require password login tokens]
private getAndroidAppClientSecret() 	{ "e749124ad386a5a35c0ab554a4f2c045" }  // Android secret
private getServerUrl() 		{ "https://graph.api.smartthings.com" }


// Automatically generated. Make future change here.
definition(
    name: "Wink Connect Service",
    namespace: "panealy",
    author: "Peter Nealy",
    description: "Connect your Wink to SmartThings.",
    category: "My Apps",
    iconUrl: "https://www.winkapp.com/assets/mediakit/mediakit-thumb-logo-icon-1562cffdc34b595e9c8ff0d9d9612b2d.png",
    iconX2Url: "https://www.winkapp.com/assets/mediakit/wink-logo-icon-6d60bd7c38ceb7c3a2f0a93e4c4cd307.png",
    oauth: true
) {
    appSetting "wink_client_id"
    appSetting "wink_client_secret"
}

mappings {
	path("/receivedToken") 			{ action:[ POST: "receivedToken", 				GET: "receivedToken"] }
	path("/receiveToken") 			{ action:[ POST: "receiveToken", 				GET: "receiveToken"] }
    path("/update")					{ action:[ GET: "eventHandler"] }
    path("/subscriptions") 			{ action:[ GET: "subscriptionData"] }
}

preferences {
    page(name: "Credentials", title: "Fetch OAuth2 Credentials", content: "authPage", install: false)
	page(name: "listDevices", title: "Wink Devices", content: "listDevices", install: true)    
    page(name: "pageLogDevices")
    //page(name: "auth")
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	//schedule("5 0,12 * * * ?", updateWinkSubscriptions) //renew subscriptions every 12 hours
	listDevices()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
    
    unschedule()
	//schedule("5 0,12 * * * ?", updateWinkSubscriptions) //renew subscriptions every 12 hours
	initialize()

	listDevices()
}

def listDevices()
{
	log.debug "In listDevices"

	def devices = getDeviceList()
	//log.debug "Device List = ${devices}"
   // log.debug "Settings List = ${settings}"

	dynamicPage(name: "listDevices", title: "Choose devices", install: true) {
		section("Devices") {
			input "devices", "enum", title: "Select Device(s)", required: false, multiple: true, options: devices
		}
	}
}

def getDeviceInfo(name, id, type)
{
	log.debug "In getDeviceInfo (type = ${type}, name = ${name}"
    
    def data = []
	if (!state.deviceDataArr.empty) {
    	def suffix = "/" + type + "/" + id
    	apiGet(suffix) { response ->
			response.data.data.each() {
            	log.debug "info for ${it.id}"
            	if (it.id == id)
                	data = it
    		}
    	}
    }
    
    return data
}

def getDeviceList()
{
	log.debug "In getDeviceList"

	def deviceList = [:]
	state.deviceDataArr = []

	apiGet("/users/me/wink_devices") { response ->
		response.data.data.each() {

			//log.debug "${it.name} (SUB: ${it.subscription})"
            // TODO to reduce redundancy, we should move the common data array fields
            // to the bottom
            if ( it.light_bulb_id ) {
            	//log.debug "LIGHT_BULB ${it}"
				deviceList["${it.light_bulb_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.light_bulb_id,
										  'type'    : "light_bulb",
                                          'subscription' : it.subscription])
                                          /*
										  'uuid'    : it.uuid,
										  'data'    : it,
                                          'manufacturer' : it.device_manufacturer,
                                          'model'	: it.model_name,
                                          'capabilities' : it.capabilities
                                          
				*/
			}
            if ( it.remote_id ) {
				deviceList["${it.remote_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.remote_id,
										  'type'    : "remote",
                						  'subscription' : it.subscription]) /*
										  'serial'  : it.serial,
                                          'uuid'	: it.uuid,
										  'data'    : it,
                                          'manufacturer' : it.device_manufacturer,
                                          'model'	: it.model_name,
										  'subscription' : it.subscription
				])*/
			}
            if ( it.smoke_detector_id ) {
            	//log.debug "SMOKE ${it}"
				deviceList["${it.smoke_detector_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.smoke_detector_id,
										  'type'    : "smoke_detector",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
                                          'uuid'	: it.uuid,
										  'data'    : it,
                                          'manufacturer' : it.device_manufacturer,
                                          'model'	: it.model_name,
										  'subscription' : it.subscription
				])*/
			}
            if ( it.garage_door_id ) {
            	//log.debug "GARAGE DOOR ${it}"
				deviceList["${it.garage_door_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.garage_door_id,
										  'type'    : "garage_door",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
                                          'uuid'	: it.uuid,
										  'data'    : it,
                                          'manufacturer' : it.device_manufacturer,
                                          'model'	: it.model_name,
										  'subscription' : it.subscription
				])*/
			}
            if ( it.thermostat_id ) {
            	//log.debug "THERMOSTAT ${it}"
				deviceList["${it.thermostat_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.thermostat_id,
										  'type'    : "remote",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
										  'data'    : it,
                                          'manufacturer' : it.device_manufacturer,
                                          'model'	: it.model_name,
										  'subscription' : it.subscription
				])*/
			}
            if ( it.sensor_pod_id ) {
            	//log.debug "SENSOR POD ${it}"
				deviceList["${it.sensor_pod_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.sensor_pod_id,
										  'type'    : "sensor_pod",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
                                          'uuid'  	: it.uuid,
										  'data'    : it,
                                          'manufacturer' : it.device_manufacturer,
                                          'model'	: it.model_name,
										  'subscription' : it.subscription
				])*/
			}
			if ( it.propane_tank_id ) {
				deviceList["${it.propane_tank_id}"] = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.propane_tank_id,
										  'type'    : "propane_tank",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
										  'data'    : it,
										  'subscription' : it.subscription
				])*/
			}
            
			if ( it.air_conditioner_id ) {
				deviceList["${it.air_conditioner_id}"] = it.name
				state.deviceDataArr.push(['name'       : it.name,
										     'id'      : it.air_conditioner_id,
										     'type'    : "air_conditioner",
                                             'subscription' : it.subscription])
                                             /*
										     'serial'  : it.serial,
										     'data'    : it,
										     'subscription' : it.subscription
				])*/
			}
                                
			if ( it.cloud_clock_id ) {
            	//log.debug "CLOUD CLOCK ${it.name} ${it.subscription}"
                def dial1Data = it.dials[0]
                deviceList[dial1Data.dial_id] =     it.name + " Dial 1"
                state.deviceDataArr.push(['name'  : it.name + " Dial 1",
                						'label'	  : dial1Data.label,
                						'id'      : dial1Data.dial_id,
										'type'    : "nimbusDial",
                                        'subscription' : it.subscription])
                                        /*
										'serial'  : it.serial,
										'data'    : it,
										'subscription' : it.subscription
				])*/
                def dial2Data = it.dials[1]
                deviceList[dial2Data.dial_id] =     it.name + " Dial 2"
                state.deviceDataArr.push(['name'  : it.name + " Dial 2",
                						'label'	  : dial2Data.label,
                						'id'      : dial2Data.dial_id,
										'type'    : "nimbusDial",
                                        'subscription' : it.subscription])
                                        /*
										'serial'  : it.serial,
										'data'    : it,
										'subscription' : it.subscription
				])*/
                def dial3Data = it.dials[2]
                deviceList[dial3Data.dial_id] =     it.name + " Dial 3"
                state.deviceDataArr.push(['name'  : it.name + " Dial 3",
                						'label'	  : dial3Data.label,
                						'id'      : dial3Data.dial_id,
										'type'    : "nimbusDial",
                                        'subscription' : it.subscription])
                                        /*
										'serial'  : it.serial,
										'data'    : it,
										'subscription' : it.subscription
				])*/
                def dial4Data = it.dials[3]
                deviceList[dial4Data.dial_id] =     it.name + " Dial 4"
                state.deviceDataArr.push(['name'  : it.name + " Dial 4",
                						'label'	  : dial3Data.label,
                						'id'      : dial4Data.dial_id,
										'type'    : "nimbusDial",
                                        'subscription' : it.subscription])
                                        /*
										'serial'  : it.serial,
										'data'    : it,
										'subscription' : it.subscription
				])*/
			} 
            
			if ( it.powerstrip_id ) {
            	def outlet1Data = it.outlets[0]
                deviceList[outlet1Data.outlet_id] = it.name + " " + outlet1Data.name
                state.deviceDataArr.push(['name'  : it.name + " " + outlet1Data.name,
										'id'      : outlet1Data.outlet_id,
										'type'    : "powerstripOutlet",
                                        'subscription' : it.subscription])
                                        /*
										'serial'  : it.serial,
										'data'    : it,
										'subscription' : it.subscription
				])*/
                
                def outlet2Data = it.outlets[1]
                deviceList[outlet2Data.outlet_id] = it.name + " " + outlet2Data.name
                state.deviceDataArr.push(['name'  : it.name + " " + outlet2Data.name,
										'id'      : outlet2Data.outlet_id,
										'type'    : "powerstripOutlet",
                                        'subscription' : it.subscription])
                                        /*
										'serial'  : it.serial,
										'data'    : it,
										'subscription' : it.subscription
				])*/
			}

			if ( it.piggy_bank_id ) {
            	//log.debug "PIGGY ${it}"
				deviceList["${it.piggy_bank_id}"]   = it.name
				state.deviceDataArr.push(['name'    : it.name,
                						  'label'	: it.label,
										  'id'      : it.piggy_bank_id,
										  'type'    : "piggy_bank",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
										  'data'    : it,
										  'subscription' : it.subscription
				])*/
			} 
                
			if ( it.eggtray_id ) {
				deviceList["${it.eggtray_id}"]      = it.name
				state.deviceDataArr.push(['name'    : it.name,
										  'id'      : it.eggtray_id,
										  'type'    : "eggtray",
                                          'subscription' : it.subscription])
                                          /*
										  'serial'  : it.serial,
										  'data'    : it,
										  'subscription' : it.subscription
				])*/
			}
		}
	}

  	return deviceList
}

def initialize()
{
	log.debug "Initialized with settings: ${settings}"

	settings.devices.each {
		def deviceId = it

		state.deviceDataArr.each {
			if ( it.id == deviceId ) {
				switch(it.type) {
                    case "light_bulb":
						log.debug "we have a Light Bulb"
                        // TODO: should we pass in device name for name, and let that flow to vendor specific devices?
						createChildDevice("Wink Light Bulb", deviceId, it.name, it.name)
                        pollLightBulb(getChildDevice(deviceId))
						break
                    case "remote":
						log.debug "we have a Remote"
						createChildDevice("Wink Remote", deviceId, it.name, it.name)
                        pollRemote(getChildDevice(deviceId))
						break
                    case "garage_door":
						log.debug "we have a garage door"
						createChildDevice("Wink Garage Door", deviceId, it.name, it.name)
                        pollGarageDoor(getChildDevice(deviceId))
						break
                    case "thermostat":
						log.debug "we have a thermostat"
						createChildDevice("Wink Thermostat", deviceId, it.name, it.name)
                        pollThermostat(getChildDevice(deviceId))
						break
                    case "sensor_pod":
						log.debug "we have a sensor"
						createChildDevice("Wink Sensor", deviceId, it.name, it.name)
                        pollSensorPod(getChildDevice(deviceId))
						break
                    case "smoke_detector":
						log.debug "we have a smoke detector"
						createChildDevice("Wink Smoke Detector", deviceId, it.name, it.name)
                        pollSmokeDetector(getChildDevice(deviceId))
						break
                	case "propane_tank":
						log.debug "we have a Refuel"
						createChildDevice("Quirky Refuel", deviceId, it.name, it.label)
                        pollPropaneTank(getChildDevice(deviceId))
						break                      
                	case "air_conditioner":
						log.debug "we have an Aros"
						createChildDevice("Quirky Aros", deviceId, it.name, it.label)
                        pollAros(getChildDevice(deviceId))
						break

					case "powerstrip":
						log.debug "we have a Pivot Power Genius"
						createPowerstripChildren(it.data) //has sub-devices, so we call out to create kids
						break
                        
					case "powerstripOutlet":
						log.debug "we have a Pivot Power Genius Outlet"
						createChildDevice( "Quirky Pivot Power Genius", deviceId, it.name, it.name )
                        pollOutlet(getChildDevice(deviceId))
						break
                        
					case "nimbusDial":
						log.debug "we have a Nimbus Dial"
						//createNimbusChildren(it.data) //has sub-devices, so we call out to create kids
                        createChildDevice( "Quirky Nimbus", deviceId, it.name, it.label )
                        pollNimbus(getChildDevice(deviceId))
						break
                        
					case "cloud_clock":
						log.debug "we have a Nimbus"
						createNimbusChildren(it.data) //has sub-devices, so we call out to create kids
                        pollNimbus(getChildDevice(deviceId))
						break
                        
					case "sensor_pod":
						log.debug "we have a Spotter"
						createChildDevice("Quirky Spotter", deviceId, it.name, it.label)
                        getSensorPodUpdate(getChildDevice(deviceId))
						break
                        
					case "piggy_bank":
						log.debug "we have a Piggy Bank [${it}] "
						createChildDevice("Quirky Porkfolio", deviceId, it.name, it.label)
                        getPiggyBankUpdate(getChildDevice(deviceId))
						break

					case "eggtray":
						log.debug "we have an Egg Minder"
						createChildDevice("Quirky Eggtray", deviceId, it.name, it.label)
                        getEggtrayUpdate(getChildDevice(deviceId))
						break                     
				}
			}                       
		}
	}
        
    // Delete any that are no longer in settings
	def delete = getChildDevices().findAll { !settings.devices?.contains(it.deviceNetworkId) }
    log.debug "deleting ${delete}"
    delete.each() {
        uninstallChildDevice(it)
		deleteChildDevice(it.deviceNetworkId)
    }
}

def createChildDevice(deviceFile, dni, name, label)
{
	log.debug "In Device"
    
    // Maybe should remove the (Wink) suffix at some point, but for now given I sometimes have redundant devices 
    // on both ST and Wink it helps me keep them straight.
    name += " (Wink)"
	try {
		def existingDevice = getChildDevice(dni)
        log.debug "existingDevice = ${existingDevice}"
		if(!existingDevice) {
			log.debug "Creating child ${app.namespace} ${deviceFile} ${dni} ${name} "
            
			def childDevice = addChildDevice(app.namespace, deviceFile, dni, null, [name: name, label: name, completedSetup: true])
		} else {
			log.debug "Device $dni already exists"
		}
        log.info "successfully created (${deviceFile} : ${name})"
	} 
    catch (e) {
		log.error "Error creating device: ${e}"
	}
}

def uninstalled()
{
	log.debug "In uninstalled"

	removeChildDevices(getChildDevices())
    
    unschedule()
}

private removeChildDevices(delete)
{
	log.debug "In removeChildDevices"
	log.debug "deleting ${delete.size()} devices"

	delete.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def uninstallChildDevice(childDevice) 
{
	log.debug "in uninstallChildDevice"

	// TODO send event to pubnub service re: subscription? Probably best they just find out themselves.
    //  - we will already return a 404 error if it tries to update a device we don't have anymore, so we 
    //    could key off of that

    //now remove the child from settings. Unselects from list of devices, not delete
    log.debug "Settings size = ${settings['devices']}"
    
    if (!settings['devices']) //empty list, bail
    	return
    
    def newDeviceList = settings['devices'] - childDevice.device.deviceNetworkId
    app.updateSetting("devices", newDeviceList)
}

def buildCallbackUrl(suffix)
{
	log.debug "In buildRedirectUrl"

	def serverUrl = getServerUrl()
	return serverUrl + "/api/token/${state.accessToken}/smartapps/installations/${app.id}" + suffix
}

def checkToken(boolean winkAndroid = false) {
	log.debug "In checkToken"
    
    def tokenStatus = "bad"
    
    def refresh_token = state.vendorRefreshToken
    def access_token = state.vendorAccessToken
    def client_id = appSettings.wink_client_id
    def client_secret = appSettings.wink_client_secret
    if (winkAndroid) {
    	refresh_token = state.androidRefreshToken
        access_token = state.androidAccessToken
        client_id = getAndroidAppClientId()
        client_secret = getAndroidAppClientSecret()
    }
    //check existing token by calling user device list
    try {
    	httpGet([ uri		: apiUrl(),
    		  	  path 		: "/users/me/wink_devices",
               	  headers 	: [ 'Authorization' : 'Bearer ' + access_token ]
		])
		{ response ->
        	debugOut "Response is: ${response.status}"
            if ( response.status == 200 ) {
            	debugOut "The current token is good"
                tokenStatus = "good"
            }
		}
	}
    catch(Exception e) {
    	debugOut "Current access token did not work. Trying refresh Token now."
    }

	if ( tokenStatus == "bad" ) {
        //Let's try the refresh token now
    	debugOut "Trying to refresh tokens"
        
    	def tokenParams = [ client_id		: client_id,
        					client_secret	: client_secret,
							grant_type		: "refresh_token",
							refresh_token	: refresh_token ]

		def tokenUrl = getVendorTokenPath() + toQueryString(tokenParams)
        
		def params = [
			uri: tokenUrl,
		]
        
    	try {
			httpPost(params) { response ->
            	debugOut "Successfully refreshed tokens with code: ${response.status}"
                if (winkAndroid) {
            		state.androidRefreshToken = response.data.refresh_token
            		state.androidAccessToken = response.data.access_token
                } else {
                	state.vendorRefreshToken = response.data.refresh_token
            		state.vendorAccessToken = response.data.access_token
                }
            	tokenStatus = "good"
        	}
		}
    	catch(Exception e) {
    		debugOut "Unable to refresh token. Error ${e}"
    	}
	}
    
    if ( tokenStatus == "bad" ) {
    	return "Error: Unable to refresh Token"
    } else {
    	return null //no errors
    }
}

def apiGet(String path, Closure callback, boolean winkAndroid = false)
{
	log.debug "In apiGet with path: $path and winkAndroid: $winkAndroid"
    
    //check to see if our token has expired
    def status = checkToken(winkAndroid)
    debugOut "Status of checktoken: ${status}"
    
    if ( status ) {
    	debugOut "Error! Status: ${status}"
        return
    } else {
    	debugOut "Token is good. Call the command"
    }
    
	def access_token = state.vendorAccessToken
    if (winkAndroid) {
    	access_token = state.androidAccessToken
    }
    debugOut "apiGet access_token = ${access_token}"
	httpGet([
		uri : apiUrl(),
		path : path,
		headers : [ 'Authorization' : 'Bearer ' + access_token ]
	])
	{
		response ->
			callback.call(response)
	}
}

def apiPut(String path, cmd, Closure callback, boolean winkAndroid = false)
{
	log.debug "In apiPut with path: $path and cmd: $cmd and winkAndroid $winkAndroid"
    
    //check to see if our token has expired
    def status = checkToken(winkAndroid)
    debugOut "Status of checktoken: ${status}"
    
    if ( status ) {
    	debugOut "Error! Status: ${status}"
        return
    } else {
    	debugOut "Token is good. Call the command"
    }
    
	def access_token = state.vendorAccessToken
    if (winkAndroid) {
    	access_token = state.androidAccessToken
    }
	httpPutJson([
		uri : apiUrl(),
		path: path,
		body: cmd,
		headers : [ 'Authorization' : 'Bearer ' + access_token ]
	])

	{
		response ->
			callback.call(response)
	}
}

def poll(childDevice)
{
	log.debug "In poll" //we should not ever get here, devices have unique polls
}

def cToF(temp) {
	return temp * 1.8 + 32
}

def fToC(temp) {
	return (temp - 32) / 1.8
}

def debugOut(msg) {

	log.debug msg
	//sendNotificationEvent(msg) //Uncomment this for troubleshooting only
}

def dollarize(int money)
{
	def value = money.toString()

	if ( value.length() == 1 )
		value = "00" + value

	if ( value.length() == 2 )
		value = "0" + value

	def newval = value.substring(0, value.length() - 2) + "." + value.substring(value.length()-2, value.length())
	value = newval

	def pattern = "\$0.00"
	def moneyform = new DecimalFormat(pattern)
	String output = moneyform.format(value.toBigDecimal())

	return output
}

def debugEvent(message, displayEvent) {

	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	log.debug "Generating AppDebug Event: ${results}"
	sendEvent (results)

}

String toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

def eventHandler()
{
	log.debug "In eventHandler (params=${params}..."

	// TODO - we really don't want to do ANOTHER query for status. The status is right in the message 
    //        from pubnub. We need to send and recieve the body.
	def object_id = params.obj_id
    def object_type = params.obj_type
    def html = """{"code":404,"message":"Not Found"}"""
    if (object_id && object_type) {
    	def dni = getChildDevice(object_id)
        if (dni) {
        	log.debug "DNI = ${dni}"
            switch (object_type) {
            	case "light_bulb":
                	html = lightBulbEventHandler(object_id)
                    break
                case "piggy_bank":
                	html = piggy_bankEventHandler(object_id)
                default:
                    break
            }
        }
    }
    
    render contentType: 'application/json', data: html
}

def displayData(display){
	render contentType: "text/html", data: """<!DOCTYPE html><html><head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0"/></head><body style="margin: 0;">${display}</body></html>"""
}

def displayJsonData(display){
	render contentType: "application/json", data: display
}

// TODO provide github link to sample node.JS code for querying this data
/*
 * SAMPLE NODE.JS TO DISPLAY subscriptions
 *
var http = require('https');

var st_appid=INSERT_APPID
var auth_token=INSERT_ACCESS_TOKEN
var url = "https://graph.api.smartthings.com:443/api/smartapps/installations/" + st_appid + "/subscriptions?&access_token=" + auth_token
http.get(url, (res) => {
  const statusCode = res.statusCode;
  const contentType = res.headers['content-type'];

  let error;
  if (statusCode !== 200) {
    error = new Error(`Request Failed.\n` +
                      `Status Code: ${statusCode}`);
  } else if (!/^application\/json/.test(contentType)) {
    error = new Error(`Invalid content-type.\n` +
                      `Expected application/json but received ${contentType}`);
  }
  if (error) {
    console.log(error.message);
    // consume response data to free up memory
    res.resume();
    return;
  }

  res.setEncoding('utf8');
  let rawData = '';
  res.on('data', (chunk) => rawData += chunk);
  res.on('end', () => {
    try {
      let parsedData = JSON.parse(rawData);
      console.log(parsedData);
    } catch (e) {
      console.log(e.message);
    }
  });
}).on('error', (e) => {
  console.log(`Got error: ${e.message}`);
});
*/
def subscriptionData() {
	log.info "Set up web page located at : ${getApiServerUrl()}/api/smartapps/installations/${app.id}/subscriptions?access_token=${state.accessToken}"

	def subs = [:]
	def devlist = getDeviceList()
    
    // TODO - not sure we need to get a fresh list all the time...
    if (devlist) {
    	settings.devices.each { 
        	def deviceId = it
            state.deviceDataArr.each {
				if ( it.id == deviceId ) {
                	log.debug "${it.name} ${it.id} ${it.subscription}" 
                    subs[it.id] = it.subscription.pubnub
                }
            }
        }
    }

    return subs
}

/////////////////////////////////////////////////////////////////////////
//	                 START LIGHT BULB SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////
def lightBulbEventHandler(objid)
{
	log.debug "In Light Bulb Event Handler..."

	def json = request.JSON
    
    def html = """{"code":200,"message":"OK"}"""
    
	def dni = getChildDevice(objid)
        
    if(!dni) //this is not a smartthings device cuz user did not pick it
    	return
            
	pollLightBulb(dni)   //sometimes events are stale, poll for all latest states
    
    return html
}

def pollLightBulb(childDevice)
{
	log.debug "Polling Light Bulb ${childDevice.device.deviceNetworkId}"
    
	apiGet("/light_bulbs/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        log.debug "Light bulb data! [${status}]"
        
        status.powered ? childDevice?.sendEvent(name:"switch",value:"on") :
		    childDevice?.sendEvent(name:"switch",value:"off")
        
        childDevice?.sendEvent(name:"level",value: (status.brightness*100))
        
        childDevice?.sendEvent(name:"connection", value: status.connection)

		// values for color bulbs (Currently based on Hue)
        childDevice?.sendEvent(name:"color_model", value: status.color_model)  
        childDevice?.sendEvent(name:"color_x", value: status.color_x)
        childDevice?.sendEvent(name:"color_y", value: status.color_y)
        childDevice?.sendEvent(name:"hue", value: status.hue)
        childDevice?.sendEvent(name:"saturation", value: status.saturation)
        childDevice?.sendEvent(name:"color_temperature", value: status.color_temperature)      
	}
}

def lightBulbOn(childDevice)
{
	apiPut("/light_bulbs/" + childDevice.device.deviceNetworkId, [desired_state : [powered : true]]) { response ->
		def data = response.data.data
		log.debug "Sending 'on' to device"
	}
}

def lightBulbOff(childDevice)
{
	apiPut("/light_bulbs/" + childDevice.device.deviceNetworkId, [desired_state : [powered : false]]) { response ->
		def data = response.data.data
		log.debug "Sending 'off' to device"
	}
}

def lightBulbLevel(childDevice, level)
{
	log.debug "desired level is ${level}"
	apiPut("/light_bulbs/" + childDevice.device.deviceNetworkId, [desired_state : [powered : true, brightness : level]]) { response ->
		def data = response.data.data
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START REMOTE SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////

def pollRemote(childDevice)
{
	log.debug "Polling Remote ${childDevice.device.deviceNetworkId}"
    
    
	apiGet("/remotes/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        log.debug "Remote data! [[${response.data}]]"
        
        //childDevice?.sendEvent(name:"needs repair", value:status.needs_repair, unit:"") 
        
        //childDevice?.sendEvent(name:"tankLevel", value:status.remaining * 100, unit:"")
        
        //childDevice?.sendEvent(name:"tankChanged", value:new Date((status.tank_changed_at as long)*1000), unit:"")
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START GARAGE DOOR SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////

def pollGarageDoor(childDevice)
{
	log.debug "Polling Garage Door ${childDevice.device.deviceNetworkId}"
    
	apiGet("/garage_doors/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        def desired = response.data.data.desired_state
        
        log.debug "Garage door data! [[${response.data}]]"
        
        if (status.position > 0.0 && desired.position > 0.0) {
        	childDevice?.sendEvent(name:"door", value:'opening', unit:"") 
        } else if (status.position > 0.0 && desired.position == 0.0) {
        	childDevice?.sendEvent(name:"door", value:'closing', unit:"")
        } else if (status.position == 0.0 && desired.position == 0.0) {
        	childDevice?.sendEvent(name:"door", value:'closed', unit:"")
        }  else if (status.position == 1.0 && desired.position == 1.0) {
        	childDevice?.sendEvent(name:"door", value:'open', unit:"")
        } else if (status.position == 1.0 && desired.position == 0.0) {
        	childDevice?.sendEvent(name:"door", value:'waiting', unit:"")
        }
        
        childDevice?.sendEvent(name:"buzzer", value:status.buzzer, unit:"")
	}
}

/* 
 * NOTE:
 * Depending on the garage door, the vendor (e.g. Chamberlain) will not allow third-party control of their doors. So we need to masquerade
 * as a Wink app in order to do this via Wink. Note you can use MyQ Lite instead for more native use in that case, and probably should, but if
 * you are integrating ecosystems than this still may be your option.
 */
def androidApiPut(String path, cmd, Closure callback)
{
	log.debug "In androidApiPut with path: $path and cmd: $cmd and winkAndroid $winkAndroid"
    
    //check to see if our token has expired
    def status = checkToken(true)
    debugOut "Status of checktoken: ${status}"
    
    if ( status ) {
    	debugOut "Error! Status: ${status}"
        return
    } else {
    	debugOut "Token is good. Call the command"
    }
    
    def access_token = state.androidAccessToken
    debugOut "Access token : ${access_token}"
	httpPutJson([
		uri : apiUrl(),
		path: path,
		body: cmd,
		headers : [ 'Authorization' : 'Bearer ' + access_token ]
	])

	{
		response ->
			callback.call(response)
	}
}

def garageDoorOpen(childDevice)
{
    def foo = [ "client_id": "quirky_wink_android_app",
    "client_secret": "e749124ad386a5a35c0ab554a4f2c045",
    "username": "panealy@gmail.com",
    "password": "kcorel69",
    "grant_type": "password" ]
    try {
    	httpPostJson("https://api.wink.com/oauth2/token", foo) { resp ->
    	    log.debug "response data: ${resp.data}"
    	    log.debug "response contentType: ${resp.contentType}"
            state.androidAccessToken = resp.data.access_token
            state.androidRefreshToken = resp.data.refresh_token
    	}
        log.debug "Successful AUTH with Android id/key"
    } catch (Exception e) {
    	log.debug "Exception ${e}"
    }
	log.debug "Sending 'open' to device /garage_doors/" + childDevice.device.deviceNetworkId
    try {
		androidApiPut("/garage_doors/" + childDevice.device.deviceNetworkId, [desired_state : [position : 1.0]]) { response ->
			def data = response.data.data
		}
    } catch (e) {
    	log.error("caught exception", e)
    }
}

def garageDoorClose(childDevice)
{
	try {
		apiPut("/garage_doors/" + childDevice.device.deviceNetworkId, [desired_state : [position : '0.0']]) { response ->
			def data = response.data.data
			log.debug "Sending 'close' to device"
		}
    } catch (e) {
    	log.error("caught exception", e)
    }
}

/////////////////////////////////////////////////////////////////////////
//	                 START SENSOR POD SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////

def pollSensorPod(childDevice)
{
	log.debug "Polling Sensor ${childDevice.device.deviceNetworkId}"
    
	apiGet("/sensor_pods/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        log.debug "Sensor data! [[${response.data}]]"
        
        status.temperature ? childDevice?.sendEvent(name:"temperature", value:cToF(status.temperature), unit:"F") :
        	childDevice?.sendEvent(name:"temperature", value: "--", unit:"")
        
        status.occupied ? childDevice?.sendEvent(name:"motion", value:'active', unit:"") :
        	childDevice?.sendEvent(name:"motion", value:'inactive', unit:"")
        
        //childDevice?.sendEvent(name:"tankLevel", value:status.remaining * 100, unit:"")
        
        //childDevice?.sendEvent(name:"tankChanged", value:new Date((status.tank_changed_at as long)*1000), unit:"")
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START THERMOSTAT SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////

def pollThermostat(childDevice)
{
	log.debug "Polling Thermostat ${childDevice.device.deviceNetworkId}"
    
	apiGet("/thermostats/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        log.debug "Thermostat data! [[${response.data}]]"
        
        //childDevice?.sendEvent(name:"needs repair", value:status.needs_repair, unit:"") 
        
        //childDevice?.sendEvent(name:"tankLevel", value:status.remaining * 100, unit:"")
        
        //childDevice?.sendEvent(name:"tankChanged", value:new Date((status.tank_changed_at as long)*1000), unit:"")
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START SMOKE DETECTOR SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////

def pollSmokeDetector(childDevice)
{
	log.debug "Polling Smoke Detector ${childDevice.device.deviceNetworkId}"
    
	apiGet("/smoke_detectors/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        log.debug "Smoke Detector data! [[${response.data}]]"
        
        //childDevice?.sendEvent(name:"needs repair", value:status.needs_repair, unit:"") 
        
        //childDevice?.sendEvent(name:"tankLevel", value:status.remaining * 100, unit:"")
        
        //childDevice?.sendEvent(name:"tankChanged", value:new Date((status.tank_changed_at as long)*1000), unit:"")
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START REFUEL SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////
def pollPropaneTank(childDevice)
{
	log.debug "Polling Refuel ${childDevice.device.deviceNetworkId}"
    
    
	apiGet("/propane_tanks/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        log.debug "Got tank data!"
        
        childDevice?.sendEvent(name:"battery", value:status.battery * 100, unit:"") 
        
        childDevice?.sendEvent(name:"tankLevel", value:status.remaining * 100, unit:"")
        
        //childDevice?.sendEvent(name:"tankChanged", value:new Date((status.tank_changed_at as long)*1000), unit:"")
	}
}

def propaneTankEventHandler()
{
	log.debug "In  propaneTankEventHandler..."

	def json = request.JSON
	def dni = getChildDevice(json.propane_tank_id)
    
	pollPropaneTank(dni)   //sometimes events are stale, poll for all latest states

	def html = """{"code":200,"message":"OK"}"""
	render contentType: 'application/json', data: html
}

/////////////////////////////////////////////////////////////////////////
//	                 START SENSOR POD SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////
def getSensorPodUpdate(childDevice)
{
	apiGet("/sensor_pods/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading

		status.loudness ? childDevice?.sendEvent(name:"sound",value:"active",unit:"") :
			childDevice?.sendEvent(name:"sound",value:"inactive",unit:"")

		status.brightness ? childDevice?.sendEvent(name:"light",value:"active",unit:"") :
			childDevice?.sendEvent(name:"light",value:"inactive",unit:"")

		status.vibration ? childDevice?.sendEvent(name:"acceleration",value:"active",unit:"") :
			childDevice?.sendEvent(name:"acceleration",value:"inactive",unit:"")

		status.external_power ? childDevice?.sendEvent(name:"powerSource",value:"powered",unit:"") :
			childDevice?.sendEvent(name:"powerSource",value:"battery",unit:"")

		childDevice?.sendEvent(name:"humidity",value:status.humidity,unit:"")

		if (status.battery != null)
			childDevice?.sendEvent(name:"battery",value:(status.battery * 100).toInteger(),unit:"")
        else
        	childDevice?.sendEvent(name:"battery",value:0,unit:"")

		// Need to get users pref of temp scale here
        if ( status.temperature != null )
			childDevice?.sendEvent(name:"temperature",value:cToF(status.temperature),unit:"F")
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START NIMBUS SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////
def createNimbusChildren(deviceData)
{
	log.debug "In createNimbusChildren [${deviceData}]"

	def nimbusName = deviceData.name
	def deviceFile = "Quirky Nimbus"
	def index = 1
	deviceData.dials.each {
		log.debug "creating dial device for ${it.dial_id}"
		def dialName = "Dial ${index}"
		def dialLabel = "${nimbusName} ${dialName}"
		createChildDevice( deviceFile, it.dial_id, dialName, dialLabel )
		index++
	}
}

def cloud_clockEventHandler()
{
	log.debug "In Nimbus Event Handler..."

	def json = request.JSON
    
	def dials = json.dials

	def html = """{"code":200,"message":"OK"}"""
	render contentType: 'application/json', data: html

	if ( dials ) {
		dials.each() {
			def childDevice = getChildDevice(it.dial_id)
            
            if (!childDevice) // not a smartthings device, user did not pick it
            	return
                
			childDevice?.sendEvent( name : "dial", value : it.label , unit : "" )
			childDevice?.sendEvent( name : "info", value : it.name , unit : "" )
		}
	}
}

def pollNimbus(dni)
{
	log.debug "In pollNimbus using dni # ${dni}"

	def dials = null

	apiGet("/users/me/wink_devices") { response ->

		response.data.data.each() {
			if (it.cloud_clock_id  ) {
				//log.debug "Found Nimbus #" + it.cloud_clock_id
				dials   = it.dials
				//log.debug dials
			}
		}
	}

	if ( dials ) {
		dials.each() {
			def childDevice = getChildDevice(it.dial_id)
            
            if (!childDevice) //this is not a SmartThings dial (user did not pick)
            	return
            
            //log.debug "Dial event ${childDevice}"
			childDevice?.sendEvent( name : "dial", value : it.label, unit : "" )
			childDevice?.sendEvent( name : "info", value : it.name , unit : "" )

			//Change the tile/icon to what info is being displayed
			switch(it.name) {
				case "Weather":
					childDevice?.setIcon("dial", "dial" ,  "st.quirky.nimbus.quirky-nimbus-weather")
					break
				case "Traffic":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-traffic")
					break
				case "Time":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-time")
					break
				case "Twitter":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-twitter")
					break
				case "Calendar":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-calendar")
					break
				case "Email":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-mail")
					break
				case "Facebook":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-facebook")
					break
				case "Instagram":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-instagram")
					break
				case "Fitbit":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-fitbit")
					break
				case "Egg Minder":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-egg")
					break
				case "Porkfolio":
					childDevice?.setIcon("dial", "dial",  "st.quirky.nimbus.quirky-nimbus-porkfolio")
					break
			}
			childDevice.save()
		}
	}
	return
}

/////////////////////////////////////////////////////////////////////////
//	                 START EGG TRAY SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////
def getEggtrayUpdate(childDevice)
{
	log.debug "In getEggtrayUpdate"

	apiGet("/eggtrays/" + childDevice.device.deviceNetworkId) { response ->

		def data = response.data.data
		def freshnessPeriod = data.freshness_period
		def trayName = data.name
		log.debug data

		int totalEggs = 0
		int oldEggs = 0

		def now = new Date()
		def nowUnixTime = now.getTime()/1000

		data.eggs.each() { it ->
			if (it != 0)
			{
				totalEggs++

				def eggArriveDate = it
				def eggStaleDate = eggArriveDate + freshnessPeriod
				if ( nowUnixTime > eggStaleDate ){
					oldEggs++
				}
			}
		}

		int freshEggs = totalEggs - oldEggs

		if ( oldEggs > 0 ) {
			childDevice?.sendEvent(name:"inventory",value:"haveBadEgg")
			def msg = "${trayName} says: "
			msg+= "Did you know that all it takes is one bad egg? "
			msg+= "And it looks like I found one.\n\n"
			msg+= "You should probably run an Egg Report before you use any eggs."
			sendNotificationEvent(msg)
		}
		if ( totalEggs == 0 ) {
			childDevice?.sendEvent(name:"inventory",value:"noEggs")
			sendNotificationEvent("${trayName} says:\n'Oh no, I'm out of eggs!'")
			sendNotificationEvent(msg)
		}
		if ( (freshEggs == totalEggs) && (totalEggs != 0) ) {
			childDevice?.sendEvent(name:"inventory",value:"goodEggs")
		}
		childDevice?.sendEvent( name : "totalEggs", value : totalEggs , unit : "" )
		childDevice?.sendEvent( name : "freshEggs", value : freshEggs , unit : "" )
		childDevice?.sendEvent( name : "oldEggs", value : oldEggs , unit : "" )
	}
}

def runEggReport(childDevice)
{
	apiGet("/eggtrays/" + childDevice.device.deviceNetworkId) { response ->

		def data = response.data.data
		def trayName = data.name
		def freshnessPeriod = data.freshness_period
		def now = new Date()
		def nowUnixTime = now.getTime()/1000

		def eggArray = []

		def i = 0

		data.eggs.each()  { it ->
			if (it != 0 ) {
				def eggArriveDate = it
				def eggStaleDate = eggArriveDate + freshnessPeriod
				if ( nowUnixTime > eggStaleDate ){
					eggArray.push("Bad  ")
				} else {
					eggArray.push("Good ")
				}
			} else {
				eggArray.push("Empty")
			}
			i++
		}

		def msg = " Egg Report for ${trayName}\n\n"
		msg+= "#7:${eggArray[6]}    #14:${eggArray[13]}\n"
		msg+= "#6:${eggArray[5]}    #13:${eggArray[12]}\n"
		msg+= "#5:${eggArray[4]}    #12:${eggArray[11]}\n"
		msg+= "#4:${eggArray[3]}    #11:${eggArray[10]}\n"
		msg+= "#3:${eggArray[2]}    #10:${eggArray[9]}\n"
		msg+= "#2:${eggArray[1]}      #9:${eggArray[8]}\n"
		msg+= "#1:${eggArray[0]}      #8:${eggArray[7]}\n"
		msg+= "                 +\n"
		msg+= "              ===\n"
		msg+= "              ==="

		sendNotificationEvent(msg)
	}
}

def eggtrayEventHandler(eggtray_id)
{
	log.debug "In  eggtrayEventHandler..."

	def dni = getChildDevice(eggtray_id)

	log.debug "event received from ${dni}"

	getEggtrayUpdate(dni) //sometimes events are stale, poll for all latest states

	def html = """{"code":200,"message":"OK"}"""
	return html // render contentType: 'application/json', data: html
}

/////////////////////////////////////////////////////////////////////////
//	                 START PIGGY BANK SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////
def getPiggyBankUpdate(childDevice)
{
	apiGet("/piggy_banks/" + childDevice.device.deviceNetworkId) { response ->
    	log.debug "${response.data.data}"
		def status = response.data.data
		def alertData = status.triggers

		if (( alertData.enabled ) && ( state.lastCheckTime )) {
			if ( alertData.triggered_at && (alertData.triggered_at[0]?.toInteger() > state.lastCheckTime) ) {
				childDevice?.sendEvent(name:"acceleration",value:"active",unit:"")
			} else {
				childDevice?.sendEvent(name:"acceleration",value:"inactive",unit:"")
			}
		}

		childDevice?.sendEvent(name:"goal",value:dollarize(status.savings_goal),unit:"")

		childDevice?.sendEvent(name:"balance",value:dollarize(status.balance),unit:"")

		def now = new Date()
		def longTime = now.getTime()/1000
		state.lastCheckTime = longTime.toInteger()
	}
}

def piggy_bankEventHandler(porkfolio_id)
{
	log.debug "In  piggy_bankEventHandler..."

	def dni = getChildDevice(porkfolio_id)

	log.debug "event received from ${dni}"

	getPiggyBankUpdate(dni) //sometimes events are stale, poll for all latest states

	def html = """{"code":200,"message":"OK"}"""
	return html // render contentType: 'application/json', data: html
}

def configurePorkfolio(childDevice, noseColor)
{
	log.debug "Requestion Porkfolio nose color ${noseColor}"
    def set_nose_color
    switch (noseColor) {
    case "Green":
    	set_nose_color = "0FFA00"
        break
    case "Yellow":
    	set_nose_color = "5E7E00"
        break
    case "Red":
    	set_nose_color = "FF005A"
        break
    case "Purple":
    	set_nose_color = "2400FF"
        break
    case "Blue":
    	set_nose_color = "00FFD2"
        break
    default:
    	set_nose_color = "FF005A"
    }

	log.debug "Sending 'config ${set_nose_color}' to device"
    def desiredState = [desired_state : [nose_color : set_nose_color]]
    try {
		apiPut("/piggy_banks/" + childDevice.device.deviceNetworkId + "/desired_state", desiredState) { response ->
			def data = response.data.data
		}
	} 
    catch (e) {
		log.error "Error configuring Porkfolio device: ${e}"
	}
}

/////////////////////////////////////////////////////////////////////////
//	                 START POWERSTRIP SPECIFIC CODE HERE
/////////////////////////////////////////////////////////////////////////

def powerstripEventHandler()
{
	log.debug "In Powerstrip Event Handler..."

	def json = request.JSON
	def outlets = json.outlets
    
    def html = """{"code":200,"message":"OK"}"""
	render contentType: 'application/json', data: html

	outlets.each() {
		def dni = getChildDevice(it.outlet_id)
        
        if(!dni) //this is not a smartthings device cuz user did not pick it
        	return
            
		pollOutlet(dni)   //sometimes events are stale, poll for all latest states
	}
}

def pollOutlet(childDevice)
{
	log.debug "In pollOutlet"

	log.debug "Polling powerstrip"
	apiGet("/outlets/" + childDevice.device.deviceNetworkId) { response ->
		def data = response.data.data
		data.powered ? childDevice?.sendEvent(name:"switch",value:"on") :
			childDevice?.sendEvent(name:"switch",value:"off")
	}
}

def outletOn(childDevice)
{
	apiPut("/outlets/" + childDevice.device.deviceNetworkId, [desired_state : [powered : true]]) { response ->
		def data = response.data.data
		log.debug "Sending 'on' to device"
	}
}

def outletOff(childDevice)
{
	apiPut("/outlets/" + childDevice.device.deviceNetworkId, [desired_state : [powered : false]]) { response ->
		def data = response.data.data
		log.debug "Sending 'off' to device"
	}
}

def createPowerstripChildren(deviceData)
{
	log.debug "In createPowerstripChildren"

	def powerstripName = deviceData.name
	//def deviceFile = "Quirky Pivot Power Genius"

	deviceData.outlets.each {
		createChildDevice( "Quirky Pivot Power Genius", it.outlet_id, it.name, "$powerstripName ${it.name}" )
        pollOutlet(getChildDevice(it.outlet_id))
	}
}

/////////////////////
// Aros stuff here
////////////////////

def arosCoolingSetpoint(childDevice, setPoint)
{
	apiPut("/air_conditioners/" + childDevice.device.deviceNetworkId, ["desired_state": setPoint]) { response ->
		def data = response.data.data
		log.debug "sending 'setPoint' to device"
	}
}

def arosFanSpeed(childDevice, fanSpeed)
{
	apiPut("/air_conditioners/" + childDevice.device.deviceNetworkId, ["desired_state": fanSpeed]) { response ->
		def data = response.data.data
        log.debug "New Fan Speed: ${fanSpeed}"
	}
}

def arosMode(childDevice, newMode)
{
	apiPut("/air_conditioners/" + childDevice.device.deviceNetworkId, [desired_state: [mode: newMode]]) { response ->
		def data = response.data.data
        log.debug "New arosMode: ${mode}"
	}
}

def arosOff(childDevice)
{
	apiPut("/air_conditioners/" + childDevice.device.deviceNetworkId, [desired_state: [powered : false]]) { response ->
		def data = response.data.data
		log.debug "Sending 'off' to device"
	}
}

def arosOn(childDevice)
{
	apiPut("/air_conditioners/" + childDevice.device.deviceNetworkId, [desired_state: [powered : true]]) { response ->
		def data = response.data.data
		log.debug "Sending 'on' to device"
	}
}

def airConditionerEventHandler()
{
	log.debug "In  airConditionerEventHandler..."

	def json = request.JSON
	def dni = getChildDevice(json.air_conditioner_id)
    
	pollAros(dni)   //sometimes events are stale, poll for all latest states

	def html = """{"code":200,"message":"OK"}"""
	render contentType: 'application/json', data: html
}

def pollAros(childDevice)
{
	log.debug "In pollAros"
	def locationScale = location.getTemperatureScale()

	//log.debug "Polling Aros ${childDevice.device.deviceNetworkId}"
    //log.debug "Location Scale: ${locationScale}"
    
	apiGet("/air_conditioners/" + childDevice.device.deviceNetworkId) { response ->
		def status = response.data.data.last_reading
        
        status.powered ? childDevice?.sendEvent(name:"switch",value:"on") :
			childDevice?.sendEvent(name:"switch",value:"off")
        log.debug "Powered Status: ${status.powered}"
        
		if ( status.temperature != null ) {	
        	if ( locationScale == "F" ) {
            	def displayValue = cToF(status.temperature) as int
                childDevice?.sendEvent(name:"temperature",value:displayValue,unit:"F")
            } else {
            	def displayValue = status.temperature as int
				childDevice?.sendEvent(name:"temperature",value:displayValue,unit:"C")
            }
            log.debug "Temperature Status: ${status.temperature}"
        }
        
        if ( status.desired_max_set_point != null ) {
			if ( locationScale == "F" ) {
            	def displaySetPointValue = cToF(status.desired_max_set_point)
                displaySetPointValue = displaySetPointValue as double
                displaySetPointValue = displaySetPointValue.trunc(2)
        		childDevice?.sendEvent(name:"coolingSetpoint",value:displaySetPointValue)
            } else {
            	def displaySetPointCValue = status.desired_max_set_point as double
                displaySetPointCValue = displaySetPointCValue.trunc(2)
            	childDevice?.sendEvent(name:"coolingSetpoint",value:displaySetPointCValue)
            } 
            log.debug "coolingSetpoint Status: ${status.desired_max_set_point}"
        }
        
        if ( status.desired_fan_speed != null ) {
        	if ( (status.desired_fan_speed == 0.333) || (status.desired_fan_speed == 0.33) )
            	childDevice?.sendEvent(name:"fanMode",value:"fanLow", descriptionText: "$childDevice fan speed is LOW")
            if ( (status.desired_fan_speed == 0.666) || (status.desired_fan_speed == 0.66) )
            	childDevice?.sendEvent(name:"fanMode",value:"fanMed", descriptionText: "$childDevice fan speed is MED")
            if ( (status.desired_fan_speed == 0.999) || (status.desired_fan_speed == 1.0) )
            	childDevice?.sendEvent(name:"fanMode",value:"fanHigh", descriptionText: "$childDevice fan speed is HIGH")    
        }
        log.debug "desired_fan_speed Status: ${status.desired_fan_speed}"
        
        if ( status.desired_mode != null ) {
        	if ( status.desired_mode == "fan_only" )
            	childDevice?.sendEvent(name:"mode",value:"fan_only", descriptionText: "$childDevice mode is FAN ONLY")
            if ( status.desired_mode == "auto_eco" )
            	childDevice?.sendEvent(name:"mode",value:"auto_eco", descriptionText: "$childDevice mode is ECO")
            if ( status.desired_mode == "cool_only" )
            	childDevice?.sendEvent(name:"mode",value:"cool_only", descriptionText: "$childDevice mode is COOL")    
        }
        log.debug "desired_mode Status: ${status.desired_mode}"
	}
}

private pageLogDevices() {
    //log.debug "Access Token: ${state.accessToken}"
    //log.debug "STappID: ${app.id} URL=https://graph.api.smartthings.com:443/api/smartapps/installations/${app.id}"

	def subs = [:]
	def devlist = getDeviceList()
    
    if (devlist) {
    	settings.devices.each { 
        	def deviceId = it
            state.deviceDataArr.each {
				if ( it.id == deviceId ) {
                	log.debug "${it.name} ${it.id} ${it.subscription}" 
                    //def list = new JsonSlurper().parseText(it.subscription)
					log.debug "${it.subscription.pubnub}"
                }
            }
        }
    }
    
	dynamicPage(name: "pageLogDevices", title: "") {
		section() {
			if (devlist) {
				paragraph "Success! Devices found. Tap Done to continue", required: false
			} else {
				paragraph "Something went wrong. We didn't find any devices.", title: "Failed to find devices.", required: true, state: null
			}
		}
	}
}

///////////////////////
// HUB STUFF
///////////////////////

private Boolean canInstallLabs()
{
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}

///////////////////////
// AUTH STUFF
///////////////////////
def OAuthToken() {
	try {
        createAccessToken()
		log.debug "Creating new Access Token"
	} catch (e) { log.error "Access Token not defined. OAuth may not be enabled. Go to the SmartApp IDE settings to enable OAuth." }
}
def authPage() {
	log.debug "In authPage"
	if(canInstallLabs()) {
		def description = null

		if (state.vendorAccessToken == null) {
			log.debug "About to create access token."

			OAuthToken()
			description = "Tap to enter Credentials."

			def redirectUrl = oauthInitUrl()

			log.debug "redirectUrl ${redirectUrl}"
			return dynamicPage(name: "Credentials", title: "Authorize Connection", nextPage:"listDevices", uninstall: true, install:false) {
				section { href url:redirectUrl, style:"embedded", required:false, title:"Connect to ${getVendorName()}:", description:description }
			}
		} else {
        
			description = "Tap 'Next' to proceed"

			return dynamicPage(name: "Credentials", title: "Credentials Accepted!", nextPage:"listDevices", uninstall: true, install:false) {
				section { href url: buildRedirectUrl("receivedToken"), style:"embedded", required:false, title:"${getVendorName()} is now connected to SmartThings!", description:description }
                
                section(title:"Device Info") {
					href "pageLogDevices", title: "Log Devices", image: "https://cdn.rawgit.com/ady624/CoRE/master/resources/images/icons/variables.png", required: false
					//href "pageStatistics", title: "Runtime Statistics", image: "https://cdn.rawgit.com/ady624/CoRE/master/resources/images/icons/statistics.png", required: false
				}
                section ("Subcriptions"){
        			if (!state.accessToken) OAuthToken()
            		if (!state.accessToken) paragraph "**You must enable OAuth via the IDE to setup this app**"
            		else href url:"${getApiServerUrl()}/api/smartapps/installations/${app.id}/subscriptions?access_token=${state.accessToken}", style:"embedded", required:false, title:"Subscriptions", description: "For pubnub service", image: "http://airpair-blog.s3.amazonaws.com/wp-content/uploads/2014/03/pn1.png"
        		}
			}
		}
	}
	else
	{
		def upgradeNeeded = """Before you can participate in SmartThings Labs we need to update your hub.

Please contact our support team at labs@smartthings.com and tell them you want access to SmartThings Labs!"""


		return dynamicPage(name:"Credentials", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
			section {
				paragraph "$upgradeNeeded"
			}
		}
	}
}
 
def oauthInitUrl() {
	log.debug "In oauthInitUrl"

	/* OAuth Step 1: Request access code with our client ID */

	state.oauthInitState = UUID.randomUUID().toString()

	def oauthParams = [ response_type: "code",
		client_id: appSettings.wink_client_id,
		state: state.oauthInitState,
		redirect_uri: buildRedirectUrl("receiveToken") ]

	return getVendorAuthPath() + toQueryString(oauthParams)
}

def buildRedirectUrl(endPoint) {
	log.debug "In buildRedirectUrl"

	return getServerUrl() + "/api/token/${state.accessToken}/smartapps/installations/${app.id}/${endPoint}"
}

def receiveToken() {
	log.debug "In receiveToken"
	def html = ""
    def badCredentials = false
    
    // TODO must replace secret here too
	def oauthParams = [ client_secret: appSettings.wink_client_secret,
		grant_type: "authorization_code",
		code: params.code ]

	def tokenUrl = getVendorTokenPath() + toQueryString(oauthParams)
	def params = [
		uri: tokenUrl,
	]

	/* OAuth Step 2: Request access token with our client Secret and OAuth "Code" */
	try
    { 
    	httpPost(params) { response ->
			def data = response.data.data

			state.vendorRefreshToken = data.refresh_token //these may need to be adjusted depending on depth of returned data
			state.vendorAccessToken = data.access_token
        	log.debug "Vendor Access Token: ${state.vendorAccessToken}"
			log.debug "Vendor Refresh Token: ${state.vendorRefreshToken}"
		}
    }
    catch(Exception e)
    {
    	badCredentials = true
        log.debug "Login unsuccessful!"
    }
    
    def displayMessage = ""
    
    if ( badCredentials ) {
    	displayMessage = "Unable to login to your " + getVendorName() + " account using these Credentials"
    } else {
    	displayMessage = "We have located your " + getVendorName() + " account."
    }

	//log.debug "token = ${state.vendorAccessToken}"
    
	/* OAuth Step 3: Use the access token to call into the vendor API throughout your code using state.vendorAccessToken. */
	html = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=100%">
        <title>${getVendorName()} Connection</title>
        <style type="text/css">
            @font-face {
                font-family: 'Swiss 721 W01 Thin';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
                font-weight: normal;
                font-style: normal;
            }
            @font-face {
                font-family: 'Swiss 721 W01 Light';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
                font-weight: normal;
                font-style: normal;
            }
            .container {
                width: 100%;
                padding: 0px;
                /*background: #eee;*/
                text-align: center;
            }
            img {
                vertical-align: middle;
            }
            img:nth-child(2) {
                margin: 0 10px;
            }
            p {
                font-size: 1.5em;
                font-family: 'Swiss 721 W01 Thin';
                text-align: center;
                color: #666666;
                padding: 0 40px;
                margin-bottom: 0;
            }
        /*
            p:last-child {
                margin-top: 0px;
            }
        */
            span {
                font-family: 'Swiss 721 W01 Light';
            }
        </style>
        </head>
        <body>
            <div class="container">
                <img src=""" + getVendorIcon() + """ alt="Vendor icon" style="width: 30%;max-height: 30%"/>
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" style="width: 10%;max-height: 10%"/>
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/st-logo%402x.png" alt="SmartThings logo" style="width: 30%;max-height: 30%"/>
                <p>$displayMessage</p>
                <p>Tap 'Done' to continue.</p>
			</div>
        </body>
        </html>
        """
	render contentType: 'text/html', data: html
}

def receivedToken() {
	log.debug "In receivedToken"

	def html = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="100%" />
        <title>Quirky Connection</title>
        <style type="text/css">
            @font-face {
                font-family: 'Swiss 721 W01 Thin';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
                font-weight: normal;
                font-style: normal;
            }
            @font-face {
                font-family: 'Swiss 721 W01 Light';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
                font-weight: normal;
                font-style: normal;
            }
            .container {
                width: 100%;
                padding: 0px;
                /*background: #eee;*/
                text-align: center;
            }
            img {
                vertical-align: middle;
            }
            img:nth-child(2) {
                margin: 0 10px;
            }
            p {
                font-size: 1.5em;
                font-family: 'Swiss 721 W01 Thin';
                text-align: center;
                color: #666666;
                padding: 0 40px;
                margin-bottom: 0;
            }
        /*
            p:last-child {
                margin-top: 0px;
            }
        */
            span {
                font-family: 'Swiss 721 W01 Light';
            }
        </style>
        </head>
        <body>
            <div class="container">
                <img src=""" + getVendorIcon() + """ alt="Vendor icon" style="width: 30%;max-height: 30%"/>
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" style="width: 10%;max-height: 10%"/>
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/st-logo%402x.png" alt="SmartThings logo" style="width: 30%;max-height: 30%"/>
                <p>Your Quirky account is connected to SmartThings. Tap 'Done' to continue to choose devices.</p>
			</div>
        </body>
        </html>
        """
	render contentType: 'text/html', data: html
}