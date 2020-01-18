#!/usr/bin/env groovy

def call(Closure body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body() // this executes the closure, which you passed when you called terraform.call() in the scripted pipeline

    //terraform_type is an environment variable specified in pipeline parameters (create or destroy)
    //backend_type is an environment variable specified in pipeline parameters (local or s3)
    slack.prefix = "[<${env.JENKINS_URL}/job/${env.JOB_NAME}/${env.BUILD_ID}/console|${env.JOB_NAME}: #${env.BUILD_NUMBER}>] "

    if (!params.backend_type && !config.backend_type) {
      log.info("backend_type not specified - assuming s3")
      backend_type = "s3"
    }

    if (!params.terraform_type && !config.terraform_type) {
      log.info("terraform_type not specified - assuming create")
      terraform_type = "create"
    }

    init(config.terraform_version, config.terraform_path, config.backend_type, config.backend_config)
    plan(config.terraform_version, config.terraform_path, config.vars_path, config.terraform_type)
    apply(config.terraform_version, config.terraform_path)
    test(config.terraform_version, config.terraform_path)

}

def init(terraform_version, terraform_path, backend_type, backend_config=[:]) {

  stage("Terraform Check") {
      sh "${terraform_path}terraform-${terraform_version} -v"
  }

  if (backend_config instanceof Map && backend_config) {

    assert backend_config.bucket
    assert backend_config.key

    if (!backend_config.containsKey("region")) {
      backend_config.region = "eu-west-2"
    }

    if (!backend_config.containsKey("acl")) {
      backend_config.acl = "bucket-owner-full-control"
    }

    stage("Terraform Init (Dynamic Backend)") {

      slack.send "Initialising terraform (Dynamic Backend)"

      def String template = ""

      backend_config.each { key, value ->
          template = template + "\n" + "${key} = \"${value}\""
      };

      writeFile(
        file: "terraform.tf",
        text: """
          terraform {
            required_version = "= ${terraform_version}"

            backend "${backend_type}" {
              ${template}
            }

          }"""
      )

      sh "${terraform_path}terraform-${terraform_version} init"

    }

  } else if (backend_config instanceof String) {

    stage("Terraform Init") {
      sh "${terraform_path}terraform-${terraform_version} init -backend-config=${backend_config}"
    }

  } else {

    log.error "Cannot determine backend config. Need to specify backend_config."
    error("Init error!")

  }

}

def plan(terraform_version, terraform_path, vars_path, type='create') {

  if (type == 'create') {

    stage("Terraform Plan") {
        slack.send "Planning infrastructure deployment with Terraform"
        sh "${terraform_path}terraform-${terraform_version} plan -var-file=${vars_path} -out=plan.json"
    }

  } else if (type == 'destroy') {

    stage("Terraform Plan") {
        slack.send "Planning infrastructure deployment with Terraform"
        sh "${terraform_path}terraform-${terraform_version} plan -destroy -var-file=${vars_path} -out=plan.json"
    }

  } else {

    log.error "Cannot determine plan type. Needs to be either create or destroy."
    error("Plan error!")

  }

}

def apply(terraform_version, terraform_path) {

  stage("Terraform Apply"){
    slack.send "Creating infrastructure from the Terraform plan"
    slack_prompt(5)
    sh "${terraform_path}terraform-${terraform_version} apply -auto-approve plan.json"
  }

}

def test(terraform_version, terraform_path) {

  stage("Terraform Tests"){
    parallel (
      steps {
        "InSpec Verifcation" {
          def exists = fileExists 'test/verify'
          if (exists) {
            sh "${terraform_path}terraform-${terraform_version} output output.json"
            inspec()
          } else {
            echo "No InSpec Verification Tests to Run."
          }
        }
        "Behave Functional Testing" {
          def exists = fileExists 'test/features'
          if (exists) {
            behave()
          } else {
            echo "No Behave Functional Tests to Run."
          }
        }
      }
    )
    slack.send "Testing infrastructure from the Terraform apply"
    slack_prompt(5)
  }

}

def slack_prompt(wait_minutes) {

  build_user_id = ""
  EXT_USER = "External User"

  try {

    wrap([$class: 'BuildUser']) {
      build_user_id = BUILD_USER_ID
      build_user = "<${env.JENKINS_URL}/user/${build_user_id}|${build_user_id}>"
    }

  } catch (Exception e) {

    try {
      build_user = "GitLab ${env.gitlabActionType} ${env.gitlabUserName}"
    } catch (Exception e2) {
      build_user = EXT_USER
    }
  }

  timeout(time: wait_minutes, unit: "MINUTES") {
    slack.send "*@here Provision infrastructure waiting for user confirmation. The request will expire and fail in ${wait_minutes}m.*", null, "yellow"
    approver = input message: "Do you want to apply?", submitterParameter: "USER"

    slack.send "Provisioning triggered by ${build_user} and approved by <${env.JENKINS_URL}/user/${approver}|${approver}>", null, "yellow"

  }

}
