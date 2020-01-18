#!/usr/bin/env groovy
File INSPEC_DIR = new File('test/verify');
if (INSPEC_DIR.isDirectory()) {
 println 'inspec exec test/verify -t aws://'.execute().text;
} else {
 System.out.println("Directory doesn't exist!!");
}
