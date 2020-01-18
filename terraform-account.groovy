#!/usr/bin/env groovy
// CHOICE/BOOLEAN PARAMETERS i.e. there's always a value
terraformType = params.TERRAFORM_TYPE
terraformVersion = params.TERRAFORM_VERSION
nodeName = params.NODE_NAME

// OPTIONAL PARAMETERS
sRepoBranch = !params.REPO_BRANCH ? "master" : params.REPO_BRANCH
varsPath = !params.VARS_PATH ? "vars/vars.tfvars" : params.VARS_PATH
backendPath = !params.BACKEND_PATH ? "vars/backend.hcl" : params.BACKEND_PATH

// REQUIRED AND CONDITIONAL PARAMETERS i.e. could be accidentally empty
if (!params.REPO_ABS_PATH) {
  error("You must specify the absolute path to the repository!")
}
sRepositoryAbsolutePath = params.REPO_ABS_PATH

// GENERATED VARIABLES
String sRepoName = sRepositoryAbsolutePath.split("/")[-1]
String sRepoPart = sRepositoryAbsolutePath[0..(sRepositoryAbsolutePath.length() - sRepositoryAbsolutePath.reverse().indexOf('/') - 2)]

//ansiColor("xterm") {

    node("$nodeName") {

      wrap([$class: 'BuildUser']) {
        build_user_id = BUILD_USER_ID
      }

      // Override the build name
      currentBuild.displayName = "#${env.BUILD_ID} ${sRepoName}"
      currentBuild.description = "Triggered by: ${build_user_id}"

      try {

        cleanWs() // Clean at start in case of resumption

//        stage("Clone Repository") {
//            gitlab.clone("${sRepoName}", [
//             branch: "${sRepoBranch}",
//              repository_group_path: "${sRepoPart}"
//            ])
//        }

        dir("$sRepoName") {

          def groovyFiles = findFiles(glob: '*.groovy')

          if (groovyFiles) {

            stage('Load optional steps') {

              groovyFiles.each {

                code = load it.name

              }

            }

          }

          stage("Run Terraform") {

            withEnv(
                [
                "AWS_METADATA_URL=fail", // This is used to force the metadata api call to fail and use profiles in ~/.aws/config
                ]
              ) {

                terraform() {

                  terraform_type = this.terraformType
                  terraform_version = this.terraformVersion
                  vars_path = this.varsPath
                  backend_config = this.backendPath
                  terraform_path = "/var/jenkins_home/tools/"

                } // end terraform.call

              } // end withEnv

          } // end Run Terraform

        } // end dir

        cleanWs()  // Clean workspace on success

      } catch (e) {

 //       slack.send "@here Failure detected: ${e}", null, "red"

        throw e

      } // end try

    } // end node

//} // end xterm
