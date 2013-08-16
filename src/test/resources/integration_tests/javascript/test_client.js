/*
 * Copyright 2012-2013 the original author or authors.
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

var container  = require( "vertx/container" ) ;
var eb         = require( "vertx/event_bus" ) ;
var vertx      = require( "vertx" ) ;
var vertxTests = require( "vertx_tests" ) ;
var vassert    = require( "vertx_assert" ) ;

var script = this ;

function testMissingToAddress() {
  eb.send( 'test.my_mailer', {
    "from": "tim@wibble.com",
    "subject": "Congratulations on your new armadillo!",
    "body": "Dear Bob, great to here you have purchased......"
  }, function( reply ) {
    vassert.assertEquals( reply.status, 'error' ) ;
    vassert.testComplete() ;
  } )
}

var breakerConfig = { 
  "address" : "breaker-addr",
  "max_failures" : 5,
  "call_timeout" : 10,
  "reset_time"   : 60,
  
  "circuits": {
    "io.vertx~mod-mongo-persistor~2.0.0-final" : {
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
    "io.vertx~mod-mailer~2.0.0-final" : {
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

var readyAddress = breakerConfig.address + '.ready'

var readyHandler = function( msg ) {
  if( msg.status === 'ok' ) {
    eb.unregisterHandler( readyAddress, readyHandler ) ;
    vertxTests.startTests( script ) ;
  }
} ;

// This will get called when the circuit breaker is
eb.registerHandler( readyAddress, readyHandler ) ;

container.deployModule( java.lang.System.getProperty( 'vertx.modulename' ), breakerConfig, 1, function( err, deployId ) {
  if (err != null) {
    err.printStackTrace();
  }
});
