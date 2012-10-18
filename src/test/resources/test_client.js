/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.eventBus;

function testMissingToAddress() {
  eb.send( 'test.my_mailer', {
    "from": "tim@wibble.com",
    "subject": "Congratulations on your new armadillo!",
    "body": "Dear Bob, great to here you have purchased......"
  }, function( reply ) {
    tu.azzert( reply.status === 'error' ) ;
    tu.testComplete() ;
  } )
}

tu.registerTests(this);
var breakerConfig = { 
  "address" : "breaker-addr",
  "max_failures" : 5,
  "call_timeout" : 10,
  "reset_time"   : 60,
  
  "circuits": {
    "vertx.mongo-persistor-v1.1" : {
      "circuitConfig": {
        "instances": 1
      },
      "modConfig": {
        "address" : "test.my_persistor",
        "host"    : "192.168.1.100",
        "port"    : 27000,
        "db_name" : "my_db"
      }
    },
    "vertx.mailer-v1.0" : {
      "circuitConfig": {
        "instances": 1
      },
      "modConfig": {
        "address": "test.my_mailer",
        "host": "smtp.googlemail.com",
        "port": 465,
        "ssl": true,
        "auth": true,
        "username": "tim",
        "password": "password"
      }
    }
  }
} ;


vertx.deployModule('com.bloidonia.circuit-breaker-v' + java.lang.System.getProperty('vertx.version'), breakerConfig, 1, function() {
  // Wait for the work-queue to power up...
  java.lang.Thread.sleep( 2000 ) ;
  tu.appReady();
});

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}