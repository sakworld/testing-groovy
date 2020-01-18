#!/usr/bin/env groovy
File INSPEC_DIR = new File('test/verify');
if (INSPEC_DIR.isDirectory()) {
 println 'behave --color --junit --no-skipped --verbose test/features'.execute().text;
} else {
 System.out.println("Directory doesn't exist!!");
}
