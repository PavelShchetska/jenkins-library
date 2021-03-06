import com.cloudbees.groovy.cps.NonCPS

def call(Map parameters = [:], body) {

    def STEP_NAME = 'dockerExecute'
    def PLUGIN_ID_DOCKER_WORKFLOW = 'docker-workflow'

    handlePipelineStepErrors(stepName: STEP_NAME, stepParameters: parameters){
        def dockerImage = parameters.get('dockerImage', '')
        Map dockerEnvVars = parameters.get('dockerEnvVars', [:])
        def dockerOptions = parameters.get('dockerOptions', '')
        Map dockerVolumeBind = parameters.get('dockerVolumeBind', [:])

        if(dockerImage) {

            if (! Jenkins.instance.pluginManager.plugins.find { p -> p.isActive() && p.getShortName() == PLUGIN_ID_DOCKER_WORKFLOW } ) {
                echo "[WARNING][${STEP_NAME}] Docker not supported. Plugin '${PLUGIN_ID_DOCKER_WORKFLOW}' is not installed or not active. Configured docker image '${dockerImage}' will not be used."
                dockerImage = null
            }

            def returnCode = sh script: 'which docker > /dev/null', returnStatus: true
            if(returnCode != 0) {
                echo "[WARNING][${STEP_NAME}] No docker environment found (command 'which docker' did not return with '0'). Configured docker image '${dockerImage}' will not be used."
                dockerImage = null
            }

            returnCode = sh script: 'docker ps -q > /dev/null', returnStatus: true
            if(returnCode != 0) {
                echo "[WARNING][$STEP_NAME] Cannot connect to docker daemon (command 'docker ps' did not return with '0'). Configured docker image '${dockerImage}' will not be used."
                dockerImage = null
            }
        }

        if(!dockerImage){
            echo "[INFO][${STEP_NAME}] Running on local environment."
            body()
        }else{
            def image = docker.image(dockerImage)
            image.pull()
            image.inside(getDockerOptions(dockerEnvVars, dockerVolumeBind, dockerOptions)) {
                body()
            }
        }
    }
}

/**
 * Returns a string with docker options containing
 * environment variables (if set).
 * Possible to extend with further options.
 * @param dockerEnvVars Map with environment variables
 */
@NonCPS
private getDockerOptions(Map dockerEnvVars, Map dockerVolumeBind, def dockerOptions) {
    def specialEnvironments = [
        'http_proxy',
        'https_proxy',
        'no_proxy',
        'HTTP_PROXY',
        'HTTPS_PROXY',
        'NO_PROXY'
    ]
    def options = ""
    if (dockerEnvVars) {
        for (String k : dockerEnvVars.keySet()) {
            options += " --env ${k}=" + dockerEnvVars[k].toString()
        }
    }

    for (String envVar : specialEnvironments) {
        if (dockerEnvVars == null || !dockerEnvVars.containsKey(envVar)) {
            options += " --env ${envVar}"
        }
    }

    if (dockerVolumeBind) {
        for (String k : dockerVolumeBind.keySet()) {
            options += " --volume ${k}:" + dockerVolumeBind[k].toString()
        }
    }

    if (dockerOptions) {
        options += " ${dockerOptions}"
    }
    return options
}
