#!/usr/bin/env groovy

// orginial from https://github.com/redhat-cop/pipeline-library/blob/master/vars/applier.groovy

class ApplierInput implements Serializable {
    //Required
    String inventoryPath          = ''
    String requirementsPath       = ''
    String ansibleRootDir         = ''
    String rolesPath              = 'galaxy'
    String applierPlaybook        = 'galaxy/openshift-applier/playbooks/openshift-cluster-seed.yml'

    //Optional
    String playbookAdditionalArgs = ''
    String secretName             = ''

    //Optional - Platform
    String clusterAPI             = ''
    String clusterToken           = ''
    Integer loglevel = 0
}

def call(Map input) {
    call(new ApplierInput(input))
}

def call(ApplierInput input) {
    assert input.inventoryPath?.trim() : "Param inventoryPath should be defined."
    assert input.requirementsPath?.trim() : "Param requirementsPath should be defined."
    assert input.ansibleRootDir?.trim() : "Param ansibleRootDir should be defined."
    assert input.rolesPath?.trim() : "Param rolesPath should be defined."
    assert input.applierPlaybook?.trim() : "Param applierPlaybook should be defined."

    openshift.loglevel(input.loglevel)

    def clusterAPI
    def clusterToken

    // if secretName is given then get cluster token from there
    // else use given clusterToken
    // useful to prevent loading the cluster token from secret over and over again
    // which can be a slow operation but also preserves backward compatibility in this function
    if(!input.secretName.allWhitespace) {
        openshift.withCluster() {
            def secretData   = openshift.selector("secret/${input.secretName}").object().data
            def encodedToken = secretData.token

            clusterToken = sh(script:"set +x; echo ${encodedToken} | base64 --decode", returnStdout: true)
            clusterAPI   = input.clusterAPI
        }
    } else {
        clusterAPI   = input.clusterAPI
        clusterToken = input.clusterToken
    }

    openshift.withCluster(clusterAPI, clusterToken) {
        openshift.withProject() {
            openshift.raw("login")

            sh """
                pushd ${input.ansibleRootDir}
                ansible-galaxy install --role-file=${input.requirementsPath} --roles-path=${input.rolesPath}
                ansible-playbook -i ${input.inventoryPath} ${input.applierPlaybook} ${input.playbookAdditionalArgs}
            	popd
	        """
        }
    }
}
