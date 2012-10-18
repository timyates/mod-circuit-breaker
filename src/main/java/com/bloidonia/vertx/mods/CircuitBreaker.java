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

package com.bloidonia.vertx.mods ;

import org.vertx.java.busmods.BusModBase ;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.Handler ;

public class CircuitBreaker extends BusModBase {
  private String address ;

  public void start() {
    super.start() ;

    address              = getOptionalStringConfig( "address", "com.bloidonia.circuit-breaker" ) ;
    Integer max_failures = getOptionalIntConfig( "max_failures", 5 ) ;
    Integer call_timeout = getOptionalIntConfig( "call_timeout", 10 ) ;
    Integer reset_time   = getOptionalIntConfig( "reset_time", 60 ) ;
    JsonObject circuits  = getOptionalObjectConfig( "circuits", new JsonObject() ) ;

    for( final String name : circuits.getFieldNames() ) {
      logger.info( String.format( "Processing %s", name ) ) ;
      JsonObject localConf     = circuits.getObject( name ) ;
      final JsonObject circuitConfig = localConf.getObject( "circuitConfig" ) ;
      JsonObject modConfig     = localConf.getObject( "modConfig" ) ;

      int instances = circuitConfig.getNumber( "instances", 1 ).intValue() ;

      String address = modConfig.getString( "address" ) ;
      circuitConfig.putString( "address", address ) ;
      String proxiedAddress = String.format( "circuit-breaker-%s", address ) ;
      circuitConfig.putString( "proxiedAddress", proxiedAddress ) ;
      modConfig.putString( "address", proxiedAddress ) ;

      circuitConfig.putNumber( "max_failures", max_failures ) ;
      circuitConfig.putNumber( "call_timeout", call_timeout ) ;
      circuitConfig.putNumber( "reset_time",   reset_time   ) ;

      logger.info( String.format( "Deploying %s (%d instances) with config %s", name, instances, modConfig.encode() ) ) ;
      container.deployModule( name, modConfig, instances, new Handler<String>() {
        public void handle( String response ) {
          logger.info( String.format( "DEPLOYED %s with response %s", name, response ) ) ;
          logger.info( String.format( "************ Deploying %s with config %s", CircuitBreakerProxy.class.getName(), circuitConfig.encode() ) ) ;
          container.deployVerticle( CircuitBreakerProxy.class.getName(), circuitConfig ) ;
        }
      } ) ;     
    }
  }

  public void stop() {
  }
}