/**
 *  DisableDimmer
 *
 *  Copyright 2016 Vikash
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
 */

definition(
    name: "DisableDimmer",
    namespace: "vvarma",
    author: "Vikash Varma",
    description: "Disable Dimmer Swtich",
    category: "Safety & Security",
    iconUrl: "http://ecx.images-amazon.com/images/I/31zw0IKIkbL.jpg",
    iconX2Url: "http://ecx.images-amazon.com/images/I/31zw0IKIkbL._AA160_.jpg"
)

preferences {
	   section("Which lights..."){
            input "lights", "capability.switchLevel", multiple: true
        }
        section([mobileOnly:true]) {
                label title: "Assign a name", required: false
                mode title: "Set for specific mode(s)", required: false
       }
}

def installed() {
	initialize()
}
def updated() {
	unsubscribe()
	initialize()
}
def initialize() {
   subscribe (lights, "switch.on",setDimHigh)
   subscribe (lights, "level",dimHandler)
   log.debug "settings = ${settings}"

}
def dimHandler(evt) {
	log.debug "setDimLevel: light=${evt.displayName} dimlevel=${evt.value}"
    for (currentLight in lights) {
    	if ("$currentLight" == "${evt.displayName}") {
        	if (Integer.parseInt(evt.value) < 30) {
            	log.debug "Switch off $currentLight since current dime level is ${evt.value}"
            	currentLight.off()
            }
        }
    }  
}
def setDimHigh(evt) {
	state.lastStatus = "on"
	for (currentLight in lights) {
            if ("$currentLight" == "${evt.displayName}") {
               currentLight.setLevel(100)
               log.debug "setting dim level for ${evt.displayName} to 100"
            }
    } 
}