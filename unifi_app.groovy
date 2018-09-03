/**
 *  UniFi NVR SmartApp
 *
 *  Copyright 2018 Chris Story
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
 *  -----------------------------------------------------------------------------------------------------------------
 *
 *  For more information, see https://github.com/cstory777/unifi_hubitat
 *  VERSION INFO:
 *  v.99.7 - Initial release to forums as beta
 *  v.99.6 - device.data changed from () to []
 *  v.99.5 - Tried to get snapshot feature working but still have issues.
 *  v.99.4 - Minor tweaks
 *  v.99.3 - Confirmed working as motion detectors and usable in Rule Machine, Simple Lighting, etc.
 *  v.99.2 - Had to add usage of api key to authenticate to NVR.
 *  v.99.1 - Got application install working on Hubitat.
 *  v.99.1 - Took code from ST Integration and made known changes for hubitat ( data to device.data, physicalgraph to hubitat, etc)
 */

definition(
    name: "UniFi NVR",
    namespace: "cstory777",
    author: "Chris Story",
    description: "UniFi NVR App for Hubitat",
    category: "Security",
    iconUrl: "https://s3.amazonaws.com/hubitat-icons/unifi_nvr/ubiquiti_nvr.png",
    iconX2Url: "https://s3.amazonaws.com/hubitat-icons/unifi_nvr/ubiquiti_nvr_2x.png",
    iconX3Url: "https://s3.amazonaws.com/hubitat-icons/unifi_nvr/ubiquiti_nvr_3x.png")


section ("preferences") {
    input name: "nvrAddress", type: "text", title: "NVR Address", description: "NVR IP address", required: true, displayDuringSetup: true, defaultValue: "0.0.0.0"
    input name: "nvrPort", type: "number", title: "NVR Port", description: "NVR HTTP port", required: true, displayDuringSetup: true, defaultValue: 7080
    input name: "username", type: "text", title: "Username", description: "Username", required: true, displayDuringSetup: true, defaultValue: "username"
    input name: "password", type: "text", title: "Password", description: "Password", required: true, displayDuringSetup: true, defaultValue: "password"
    input name: "apiKey", type: "text", title: "Api Key", description: "Api Key", required: true, displayDuringSetup: true, defaultValue: ""
}

/**
 * installed() - Called by Hubitat Platform during installation
 */
def installed() {
    log.info "UniFi NVR: installed with settings: ${settings}"
}

/**
 * updated() - Called by Hubitat Platform whenever you make changes
 */
def updated() {
    log.info "UniFi NVR: updated with settings: ${settings}"
    nvr_initialize()
}

/**
 * nvr_initialize() - Clear state and poll the bootstrap API and the result is handled by nvr_bootstrapPollCallback
 */


def nvr_initialize()
{
    state.nvrName = "Unknown"
    state.loginCookie = null;


    state.nvrTarget = "${settings.nvrAddress}:${settings.nvrPort}"
    log.info "nvr_initialize: NVR API is located at ${state.nvrTarget}"

    def hubAction = new hubitat.device.HubAction(
        [
            path: "/api/2.0/bootstrap?apiKey=${apiKey}",
            method: "GET",
            HOST: state.nvrTarget,
            body: "{\"email\":\"${settings.username}\", \"password\":\"${settings.password}\"}",
            headers: [
                "Host":"${state.nvrTarget}",
                "Accept":"application/json",
                "Content-Type":"application/json"
            ]
        ],
        null,
        [
            callback: nvr_loginCallback
        ]
    );

    sendHubCommand( hubAction );
}

/**
 * nvr_loginCallback() - Callback from hubAction that sends the login API request
 */
def nvr_loginCallback( hubitat.device.HubResponse hubResponse )
{
    if( hubResponse.status != 200 )
    {
        log.error "nvr_loginCallback: unable to login.  Please check IP, username and password.  Status ${hubResponse.status}.";
        return;
    }

    String setCookieHeader = hubResponse?.headers['set-cookie'];

    if( !setCookieHeader )
    {
        log.error "nvr_loginCallback: no headers found for login token.  Please check IP, username and password.";
        return;
    }

    // JSESSIONID_AV is the login cookie we need to use for other API calls
    def cookies = setCookieHeader.split(";").inject([:]) { cookies, item ->
        def nameAndValue = item.split("=");
        if( nameAndValue[0] == "JSESSIONID_AV" )
        {
            state.loginCookie = nameAndValue[1];
        }
    }

    if( !state.loginCookie )
    {
        log.error "nvr_loginCallback: unable to login.  Please check IP, username and password.";
        log.debug "nvr_loginCallback: loginCookie is ${loginCookie}";
        return;
    }
    else
    {
        log.info "nvr_loginCallback: login successful!";
    }

    // If there is no API key or its off, the cameras won't work.
    state.apiKey = hubResponse.json?.data?.apiKey[0];

    def hubAction = new hubitat.device.HubAction(
        [
	        path: "/api/2.0/bootstrap?apiKey=${apiKey}",
            method: "GET",
            HOST: state.nvrTarget,
            headers: [
                "Host":"${state.nvrTarget}",
                "Accept":"application/json",
                "Content-Type":"application/json",
                "Cookie":"JSESSIONID_AV=${state.loginCookie}"
            ]
        ],
        null,
        [
            callback: nvr_bootstrapPollCallback
        ]
    );

    sendHubCommand( hubAction );
}

/**
 * nvr_bootstrapPollCallback() - Callback from HubAction with result from GET
 */
def nvr_bootstrapPollCallback( hubitat.device.HubResponse hubResponse )
{
    def data = hubResponse.json?.data

    if( !data || !data.isLoggedIn )
    {
    	log.error "nvr_bootstrapPollCallback: unable to get data from NVR!"
        return
    }

    if( data.isLoggedIn[0] != true )
    {
    	log.error "nvr_bootstrapPollCallback: unable to log in!  Please check API key."
        return
    }

    state.nvrName = data.servers[0].name[0]
    log.info "nvr_bootstrapPollCallback: response from ${state.nvrName}"

    def camerasProcessed = 0

    data.cameras[0].each { camera ->
        def dni = "${camera.mac}"
        def child = getChildDevice( dni )

        ++camerasProcessed

        if( child )
        {
            log.info "nvr_bootstrapPollCallback: already have child ${dni}"
            child.updated()
        }
        else
        {
            def metaData = [   "label" : camera.name + " (" + camera.model + ")",
                               "data": [
                                   "uuid" : camera.uuid,
                                   "name" : camera.name,
                                   // 1st generation: doesn't enumerate the 'camera.platform', uses camera.uuid for API calls
                                   // 2nd generation: camera.platform = "GEN2", uses camera._id for API calls
                                   // 3rd generation: camera.platform = "GEN3L", uses camera._id for API calls
                                   "id" : camera.platform ? camera._id : camera.uuid
                               ]
                           ]

            log.info "nvr_bootstrapPollCallback: adding child ${dni} ${metaData}"

            try
            {
                addChildDevice( "cstory777", "UniFi NVR Camera", dni, location.hubs[0].id, metaData )
            }
            catch( exception )
            {
                log.error "nvr_bootstrapPollCallback: adding child ${dni} failed (child probably already exists), continuing..."
            }
        }
    }

    log.info "nvr_bootstrapPollCallback: processed ${camerasProcessed} cameras"
}

/**
 * _getApiKey() - Here for the purpose of children
 */
def _getApiKey()
{
    return apiKey
}
/**
 * _getNvrTarget() - Here for the purpose of children
 */
def _getNvrTarget()
{
    return state.nvrTarget
}
